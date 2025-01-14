package net.wiringbits.components.widgets

import com.alexitc.materialui.facade.materialUiCore.mod.PropTypes.Color
import com.alexitc.materialui.facade.materialUiCore.{components => mui, materialUiCoreStrings => muiStrings}
import net.wiringbits.api.forms.StatefulFormData
import net.wiringbits.forms.SignInFormData
import net.wiringbits.models.User
import net.wiringbits.ui.components.inputs.{EmailInput, PasswordInput}
import net.wiringbits.webapp.utils.slinkyUtils.components.core.ErrorLabel
import net.wiringbits.webapp.utils.slinkyUtils.components.core.widgets.Container.{Alignment, EdgeInsets}
import net.wiringbits.webapp.utils.slinkyUtils.components.core.widgets.{CircularLoader, Container, Title}
import net.wiringbits.{API, AppStrings}
import org.scalajs.dom
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits._
import slinky.core.annotations.react
import slinky.core.facade.{Fragment, Hooks}
import slinky.core.{FunctionalComponent, SyntheticEvent}
import slinky.web.html._
import typings.reactRouterDom.{mod => reactRouterDom}

import scala.util.{Failure, Success}

@react object SignInForm {
  case class Props(api: API, loggedIn: User => Unit, captchaKey: String)

  val component: FunctionalComponent[Props] = FunctionalComponent[Props] { props =>
    val history = reactRouterDom.useHistory()
    val (formData, setFormData) = Hooks.useState(
      StatefulFormData(
        SignInFormData.initial(
          emailLabel = AppStrings.email,
          passwordLabel = AppStrings.password
        )
      )
    )

    def onDataChanged(f: SignInFormData => SignInFormData): Unit = {
      setFormData { current =>
        current.filling.copy(data = f(current.data))
      }
    }

    def handleSubmit(e: SyntheticEvent[_, dom.Event]): Unit = {
      e.preventDefault()

      if (formData.isSubmitButtonEnabled) {
        setFormData(_.submit)
        for {
          request <- formData.data.submitRequest
            .orElse {
              setFormData(_.submissionFailed("Complete the necessary data"))
              None
            }
        } yield props.api.client
          .login(request)
          .onComplete {
            case Success(res) =>
              setFormData(_.submitted)
              props.loggedIn(User(res.name, res.email, res.token))
              history.push("/dashboard") // redirects to the dashboard

            case Failure(ex) =>
              setFormData(_.submissionFailed(ex.getMessage))
          }
      } else {
        println("Submit fired when it is not available")
      }
    }

    val emailInput = Container(
      minWidth = Some("100%"),
      margin = EdgeInsets.bottom(8),
      child = EmailInput
        .component(
          EmailInput.Props(
            formData.data.email,
            disabled = formData.isInputDisabled,
            onChange = value => onDataChanged(x => x.copy(email = x.email.updated(value)))
          )
        )
    )

    val passwordInput = Container(
      minWidth = Some("100%"),
      margin = EdgeInsets.bottom(16),
      child = PasswordInput
        .component(
          PasswordInput.Props(
            formData.data.password,
            disabled = formData.isInputDisabled,
            onChange = value => onDataChanged(x => x.copy(password = x.password.updated(value)))
          )
        )
    )

    val error = formData.firstValidationError.map { text =>
      Container(
        margin = Container.EdgeInsets.top(16),
        child = ErrorLabel(text)
      )
    }

    val recaptcha = ReCaptcha(
      onChange = captchaOpt => onDataChanged(x => x.copy(captcha = captchaOpt)),
      props.captchaKey
    )

    val loginButton = {
      val text =
        if (formData.isSubmitting)
          Fragment(
            CircularLoader(),
            Container(margin = EdgeInsets.left(8), child = AppStrings.loading)
          )
        else
          Fragment(AppStrings.login)

      mui
        .Button(text)
        .fullWidth(true)
        .disabled(formData.isSubmitButtonDisabled)
        .variant(muiStrings.contained)
        .color(Color.primary)
        .`type`(muiStrings.submit)
    }

    // TODO: Use a form to get the enter key submitting the form
    form(
      onSubmit := (handleSubmit(_))
    )(
      mui
        .Paper()
        .elevation(1)(
          Container(
            minWidth = Some("300px"),
            alignItems = Alignment.center,
            padding = EdgeInsets.all(16),
            child = Fragment(
              Title(AppStrings.signIn),
              emailInput,
              passwordInput,
              recaptcha,
              error,
              Container(
                minWidth = Some("100%"),
                margin = EdgeInsets.top(16),
                alignItems = Alignment.center,
                child = loginButton
              )
            )
          )
        )
    )
  }
}
