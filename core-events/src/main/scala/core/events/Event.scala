package core.events

import scala.language.implicitConversions

sealed trait Event {
  def id: String
  def componentId: String
  def >>(f1: (EventFieldWithValue)*)(implicit ctx: WithEvents, publisher: EventPublisher, system: CtxSystem) = publisher.publish(this, f1)
}

object EventOps {
  var map = Map[Symbol, SimpleField]()

  implicit def stringToEventOps(s: String)(implicit component: CtxComponent): EventOps = new EventOps(s, component)
  implicit def symbolToEventOps(s: Symbol)(implicit component: CtxComponent): EventOps = new EventOps(s.name, component)

  implicit def symbolToEventField(s: Symbol) : EventField = map.getOrElse(s, {
    val v = SimpleField(s.name)
    map = map + (s -> v)
    v
  })

}

class EventOps(id: String, component: CtxComponent) {
  def trace : Event = TraceEvent(id, component.id)
  def info : Event = InfoEvent(id, component.id)
  def warn : Event = WarnEvent(id, component.id)
  def error : Event = ErrorEvent(id, component.id)

}

case class TraceEvent(id: String, componentId: String) extends Event
case class InfoEvent(id: String, componentId: String) extends Event
case class WarnEvent(id: String, componentId: String) extends Event
case class ErrorEvent(id: String, componentId: String) extends Event



