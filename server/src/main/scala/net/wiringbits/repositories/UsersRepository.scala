package net.wiringbits.repositories

import net.wiringbits.common.models.Email
import net.wiringbits.executors.DatabaseExecutionContext
import net.wiringbits.repositories.daos.{UserLogsDAO, UsersDAO}
import net.wiringbits.repositories.models.{User, UserLog}
import play.api.db.Database

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.Future

class UsersRepository @Inject() (database: Database)(implicit ec: DatabaseExecutionContext) {

  def create(request: User.CreateUser): Future[Unit] = Future {
    database.withConnection { implicit conn =>
      UsersDAO.create(request)
    }
  }

  def all(): Future[List[User]] = Future {
    database.withConnection { implicit conn =>
      UsersDAO.all()
    }
  }

  def find(email: Email): Future[Option[User]] = Future {
    database.withConnection { implicit conn =>
      UsersDAO.find(email)
    }
  }

  def find(userId: UUID): Future[Option[User]] = Future {
    database.withConnection { implicit conn =>
      UsersDAO.find(userId)
    }
  }

  def update(userId: UUID, name: String): Future[Unit] = Future {
    database.withConnection { implicit conn =>
      UsersDAO.updateName(userId, name)
    }
  }

  def verify(userId: UUID): Future[Unit] = Future {
    database.withConnection { implicit conn =>
      UsersDAO.verify(userId)
    }
  }

  def resetPassword(userId: UUID, password: String): Future[Unit] = Future {
    database.withTransaction { implicit conn =>
      UsersDAO.resetPassword(userId, password)
      val request = UserLog.CreateUserLog(UUID.randomUUID(), userId, "Password was reset")
      UserLogsDAO.create(request)
    }
  }
}
