package core.sysevents

trait SyseventComponent {

  implicit val component = this

  def componentId: String
}
