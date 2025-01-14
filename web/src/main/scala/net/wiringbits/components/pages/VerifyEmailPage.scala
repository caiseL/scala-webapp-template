package net.wiringbits.components.pages

import com.alexitc.materialui.facade.csstype.mod.{FlexDirectionProperty, TextAlignProperty}
import com.alexitc.materialui.facade.materialUiCore.createMuiThemeMod.Theme
import com.alexitc.materialui.facade.materialUiCore.{components => mui, materialUiCoreStrings => muiStrings}
import com.alexitc.materialui.facade.materialUiStyles.makeStylesMod.StylesHook
import com.alexitc.materialui.facade.materialUiStyles.mod.makeStyles
import com.alexitc.materialui.facade.materialUiStyles.withStylesMod.{
  CSSProperties,
  StyleRulesCallback,
  Styles,
  WithStylesOptions
}
import net.wiringbits.AppStrings
import org.scalablytyped.runtime.StringDictionary
import slinky.core.FunctionalComponent
import slinky.core.annotations.react
import slinky.core.facade.Fragment
import slinky.web.html.{br, className, div}

@react object VerifyEmailPage {
  type Props = Unit

  private lazy val useStyles: StylesHook[Styles[Theme, Unit, String]] = {
    val stylesCallback: StyleRulesCallback[Theme, Unit, String] = _ =>
      StringDictionary(
        "emailPage" -> CSSProperties()
          .setFlex(1)
          .setDisplay("flex")
          .setFlexDirection(FlexDirectionProperty.column)
          .setAlignItems("center")
          .setTextAlign(TextAlignProperty.center)
          .setJustifyContent("center"),
        "emailTitle" -> CSSProperties()
          .setFontWeight(600),
        "emailPhoto" -> CSSProperties()
          .setWidth("10rem")
          .setPadding("15px 0")
      )
    makeStyles(stylesCallback, WithStylesOptions())
  }

  val component: FunctionalComponent[Props] = FunctionalComponent[Props] { props =>
    val classes = useStyles(())

    Fragment(
      div(className := classes("emailPage"))(
        Fragment(
          mui
            .Typography(AppStrings.verifyYourEmailAddress)
            .variant(muiStrings.h5)
            .className(classes("emailTitle")),
          br(),
          mui
            .Typography(
              AppStrings.emailHasBeenSent
            )
            .variant(muiStrings.h6),
          mui
            .Typography(
              AppStrings.emailNotReceived
            )
            .variant(muiStrings.h6)
        )
      )
    )
  }

}
