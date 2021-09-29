package net.wiringbits.components.pages

import net.wiringbits.API
import net.wiringbits.components.widgets.ExperimentalTableWidget
import slinky.core.FunctionalComponent
import slinky.core.annotations.react
import slinky.core.facade.Fragment

@react object ExperimentalTablesPage {
  case class Props(api: API)

  val component: FunctionalComponent[Props] = FunctionalComponent[Props] { props =>
    ExperimentalTableWidget(props.api)
  }

}
