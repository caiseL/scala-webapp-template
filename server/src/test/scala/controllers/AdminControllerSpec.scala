package controllers

import controllers.common.PlayPostgresSpec

import scala.util.control.NonFatal

class AdminControllerSpec extends PlayPostgresSpec {

  "GET /admin/tables/users" should {
    "return users database" in withApiClient { client =>
      val response = client.adminGetTableMetadata("users").futureValue
      response.name must be("users")
      response.columns mustNot be(empty)
    }

    "fail when database doesn't exists" in withApiClient { client =>
      val error = client
        .adminGetTableMetadata("adklsadasldklda")
        .map(_ => "Success when failure expected")
        .recover { case NonFatal(ex) =>
          ex.getMessage
        }
        .futureValue
      error must be("Invalid table")

    }
  }

}
