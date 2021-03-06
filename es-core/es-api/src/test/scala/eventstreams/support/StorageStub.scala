package eventstreams.support

import com.typesafe.config.Config
import eventstreams.core.storage.Storage
import eventstreams.support.StorageStub.{StoredEntry, storage}
import net.ceedubs.ficus.Ficus._

import scala.collection.mutable
import scalaz.Scalaz._


object StorageStub {

  case class StoredEntry(config: String, meta: String, state: Option[String])

  private var map = mutable.Map[Int, mutable.Map[String, StoredEntry]]()

  def storage = map
  
  def clear() = storage.synchronized {
    storage.clear()
  }
  
}

class StorageStub(implicit config: Config) extends Storage {

  val configStorageInstance = config.as[Option[Int]]("test.instanceId") | 1

  def myStorage = storage.getOrElseUpdate(configStorageInstance, mutable.Map())
  
  override def store(key: String, config: String, meta: String, state: Option[String]): Unit = storage.synchronized {
    myStorage.put(key, StoredEntry(config, meta, state))
  }

  override def retrieveAllMatching(key: String): List[(String, String, String, Option[String])] =
    storage.synchronized {
      myStorage.collect {
        case (k, StoredEntry(c, m, s)) if k.startsWith(key) => (k, c, m, s)
      }.toList
    }


  override def storeState(key: String, state: Option[String]): Unit = storage.synchronized {
    myStorage += key -> myStorage.getOrElse(key, StoredEntry("","", None)).copy(state = state)
  }

  override def remove(key: String): Unit = storage.synchronized {
    myStorage -= key
  }

  override def retrieve(key: String): Option[(String, String, Option[String])] = storage.synchronized {
    myStorage.get(key).map { e =>
      (e.config, e.meta, e.state)
    }
  }

  override def storeConfig(key: String, config: String): Unit = storage.synchronized {
    myStorage += key -> myStorage.getOrElse(key, StoredEntry("", "", None)).copy(config = config)
  }

  override def storeMeta(key: String, meta: String): Unit = storage.synchronized {
    myStorage += key -> myStorage.getOrElse(key, StoredEntry("", "", None)).copy(meta = meta)
  }
}
