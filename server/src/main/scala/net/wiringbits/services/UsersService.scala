package net.wiringbits.services

import net.wiringbits.api.models.{
  CreateUser,
  ForgotPassword,
  GetCurrentUser,
  Login,
  ResetPassword,
  UpdateUser,
  VerifyEmail
}
import net.wiringbits.apis.{EmailApiAWSImpl, ReCaptchaApi}
import net.wiringbits.apis.models.EmailRequest
import net.wiringbits.common.models.{Captcha, Email, Password}
import net.wiringbits.config.{JwtConfig, UserTokensConfig, WebAppConfig}
import net.wiringbits.repositories
import net.wiringbits.repositories.models.{User, UserToken, UserTokenType}
import net.wiringbits.repositories.{UserLogsRepository, UserTokensRepository, UsersRepository}
import net.wiringbits.util.{EmailMessage, JwtUtils}
import org.mindrot.jbcrypt.BCrypt

import java.time.{Clock, Instant}
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class UsersService @Inject() (
    jwtConfig: JwtConfig,
    repository: UsersRepository,
    userLogsRepository: UserLogsRepository,
    userTokensRepository: UserTokensRepository,
    webAppConfig: WebAppConfig,
    userTokensConfig: UserTokensConfig,
    emailApi: EmailApiAWSImpl,
    captchaApi: ReCaptchaApi,
    clock: Clock
)(implicit
    ec: ExecutionContext
) {

  // returns the login token
  def create(request: CreateUser.Request): Future[CreateUser.Response] = {
    val validations = {
      for {
        _ <- validateCaptcha(request.captcha)
        _ <- validateEmail(request.email)
      } yield ()
    }

    for {
      _ <- validations
      hashedPassword = BCrypt.hashpw(request.password.string, BCrypt.gensalt())
      createUser = repositories.models.User
        .CreateUser(id = UUID.randomUUID(), name = request.name, email = request.email, hashedPassword = hashedPassword)
      _ <- repository.create(createUser)
      _ <- userLogsRepository.create(
        createUser.id,
        s"Account created, name = ${request.name}, email = ${request.email}"
      )
      createToken = UserToken
        .Create(
          id = UUID.randomUUID(),
          token = UUID.randomUUID.toString,
          tokenType = UserTokenType.EmailVerification,
          createdAt = Instant.now(clock),
          userId = createUser.id,
          expiresAt = Instant.now(clock).plus(userTokensConfig.emailVerificationExp.toHours, ChronoUnit.HOURS)
        )
      _ <- userTokensRepository.create(createToken)
      emailParameter = s"${createUser.id}_${createToken.token}"
      emailRequest = EmailMessage.registration(
        name = createUser.name,
        url = webAppConfig.host,
        emailParameter = emailParameter
      )
      _ = emailApi.sendEmail(EmailRequest(request.email, emailRequest))
    } yield CreateUser.Response(id = createUser.id, email = createUser.email, name = createUser.name)
  }

  def verifyEmail(userId: UUID, token: UUID): Future[VerifyEmail.Response] = for {
    userMaybe <- repository.find(userId)
    user = userMaybe.getOrElse(throw new RuntimeException(s"User wasn't found"))
    _ = if (user.verifiedOn.isDefined)
      throw new RuntimeException(s"User $userId email is already verified")
    tokenMaybe <- userTokensRepository.find(userId, token)
    token = tokenMaybe.getOrElse(throw new RuntimeException(s"Token for user $userId wasn't found"))
    _ = enforceValidToken(token)
    _ <- repository.verify(userId)
    _ <- userLogsRepository.create(userId, "Email was verified")
    _ <- userTokensRepository.delete(token.id, userId)
    _ = emailApi.sendEmail(EmailRequest(user.email, EmailMessage.confirm(user.name)))
  } yield VerifyEmail.Response()

  // returns the token to use for authenticating requests
  def login(request: Login.Request): Future[Login.Response] = {
    for {
      _ <- validateCaptcha(request.captcha)
      maybe <- repository.find(request.email)
      _ = if (maybe.flatMap(_.verifiedOn).isEmpty)
        throw new RuntimeException("The email is not verified, check your spam folder if you don't see the email.")
      user = maybe
        .filter(user => BCrypt.checkpw(request.password.string, user.hashedPassword))
        .getOrElse(throw new RuntimeException("The given email/password doesn't match"))
      _ <- userLogsRepository.create(user.id, "Logged in successfully")
      token = JwtUtils.createToken(jwtConfig, user.id)(clock)
    } yield Login.Response(user.id, user.name, user.email, token)
  }

  def forgotPassword(request: ForgotPassword.Request): Future[ForgotPassword.Response] = {

    def whenExists(user: User) = {
      val createToken = UserToken
        .Create(
          id = UUID.randomUUID(),
          token = UUID.randomUUID.toString,
          tokenType = UserTokenType.ResetPassword,
          createdAt = Instant.now(clock),
          userId = user.id,
          expiresAt = Instant.now(clock).plus(userTokensConfig.resetPasswordExp.toHours, ChronoUnit.HOURS)
        )
      enforceVerifiedUser(user)
      val emailMessage = EmailMessage.forgotPassword(user.name, webAppConfig.host, s"${user.id}_${createToken.id}")
      for {
        _ <- userTokensRepository.create(createToken)
        _ = emailApi.sendEmail(EmailRequest(user.email, emailMessage))
      } yield ()
    }

    for {
      _ <- validateCaptcha(request.captcha)
      userMaybe <- repository.find(request.email)
      _ <- userMaybe.map(whenExists).getOrElse(Future.unit)
    } yield ForgotPassword.Response()
  }

  def resetPassword(userId: UUID, token: UUID, password: Password): Future[ResetPassword.Response] = {
    val hashedPassword = BCrypt.hashpw(password.string, BCrypt.gensalt())
    for {
      tokenMaybe <- userTokensRepository.find(userId, token)
      token = tokenMaybe.getOrElse(throw new RuntimeException(s"Token for user $userId wasn't found"))
      _ = enforceValidToken(token)
      userMaybe <- repository.find(userId)
      user = userMaybe.getOrElse(throw new RuntimeException(s"User with id $userId wasn't found"))
      _ <- repository.resetPassword(userId, hashedPassword)
      emailMessage = EmailMessage.resetPassword(user.name)
      _ = emailApi.sendEmail(EmailRequest(user.email, emailMessage))
      token = JwtUtils.createToken(jwtConfig, user.id)(clock)
    } yield ResetPassword.Response(token)
  }

  private def enforceVerifiedUser(user: User): Unit = {
    if (user.verifiedOn.isEmpty) throw new RuntimeException(s"User's ${user.email} hasn't been verified yet")
  }

  private def enforceValidToken(token: UserToken): Unit = {
    if (token.expiresAt.isBefore(clock.instant())) throw new RuntimeException("Token is expired")
  }

  def update(userId: UUID, request: UpdateUser.Request): Future[Unit] = {
    val validate = Future {
      if (request.name.isEmpty) new RuntimeException(s"the name is required")
      else ()
    }

    for {
      _ <- validate
      user <- unsafeUser(userId)
      _ <- repository.update(userId, request.name)
      _ <- userLogsRepository.create(userId, s"Name changed from ${user.name} to ${request.name}")
    } yield ()
  }

  def getCurrentUser(userId: UUID): Future[GetCurrentUser.Response] = {
    for {
      user <- unsafeUser(userId)
    } yield GetCurrentUser.Response(
      id = user.id,
      email = user.email,
      name = user.name
    )
  }

  private def unsafeUser(userId: UUID): Future[User] = {
    repository
      .find(userId)
      .map { maybe =>
        maybe.getOrElse(
          throw new RuntimeException(
            s"Unexpected error because the user wasn't found: $userId"
          )
        )
      }
  }

  private def validateEmail(email: Email): Future[Unit] = {
    for {
      maybe <- repository.find(email)
    } yield {
      if (maybe.isDefined) throw new RuntimeException(s"Email already in use, pick another one")
      else ()
    }
  }

  private def validateCaptcha(captcha: Captcha): Future[Unit] = {
    captchaApi
      .verify(captcha)
      .map(valid => if (!valid) throw new RuntimeException(s"Invalid captcha, try again") else ())
  }
}
