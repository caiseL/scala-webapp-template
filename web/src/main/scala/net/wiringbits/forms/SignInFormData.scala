package net.wiringbits.forms

import net.wiringbits.api.forms.{FormData, FormField}
import net.wiringbits.api.models.Login
import net.wiringbits.common.models.{Captcha, Email, Password}

case class SignInFormData(
    email: FormField[Email],
    password: FormField[Password],
    captcha: Option[Captcha] = None
) extends FormData[Login.Request] {
  override def fields: List[FormField[_]] = List(email, password)

  override def formValidationErrors: List[String] = {
    List(
      fieldsError
    ).flatten
  }

  override def submitRequest: Option[Login.Request] = {
    val formData = this
    for {
      email <- formData.email.valueOpt
      password <- formData.password.valueOpt
      captcha <- formData.captcha
    } yield Login.Request(
      email,
      password,
      captcha
    )
  }
}

object SignInFormData {

  def initial(
      emailLabel: String,
      passwordLabel: String
  ): SignInFormData = SignInFormData(
    email = new FormField(label = emailLabel, name = "email", required = true, `type` = "email"),
    password = new FormField(label = passwordLabel, name = "password", required = true, `type` = "password")
  )
}
