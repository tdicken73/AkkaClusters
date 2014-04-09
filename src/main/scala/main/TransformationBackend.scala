package main

import com.typesafe.config.ConfigFactory

import akka.actor.ActorSystem
import akka.cluster.ClusterEvent.CurrentClusterState
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.MemberUp
import akka.cluster.Member
import akka.actor.Actor
import akka.event.Logging
import akka.actor.Props
import akka.actor.RootActorPath
import akka.cluster.MemberStatus
import org.fusesource.lmdbjni._
import java.util.Arrays
import java.io.File

object TransformationBackend {
  def main(args: Array[String]): Unit = {
    // Override the configuration of the port when specified as program argument
    val config =
      (if (args.nonEmpty) ConfigFactory.parseString(s"akka.remote.netty.tcp.port=${args(0)}")
      else ConfigFactory.empty).withFallback(
        ConfigFactory.parseString("akka.cluster.roles = [backend]")).
        withFallback(ConfigFactory.load())

    val system = ActorSystem("ClusterSystem", config)
    system.actorOf(Props[TransformationBackend], name = "backend")
    
  }
}

//#backend
class TransformationBackend extends Actor {

  val log = Logging(context.system, this)
  val cluster = Cluster(context.system)
  val env = new Env()
  val file = new File("/Users/corydobson/Desktop/LightSpeedClusters/"+this.toString())
  file.mkdir()
  env.open(file.toString());
  val db = env.openDatabase(this.toString())
  // subscribe to cluster changes, MemberUp
  // re-subscribe when restart
  
  override def preStart(): Unit = { cluster.subscribe(self, classOf[MemberUp])
  							//start up local lightning cluster ?
		  					
    					   
      
  } 
  
  override def postStop(): Unit = cluster.unsubscribe(self)

  def receive = {
    case TransformationJob(text) => 
      log.info("received: " + text)
      
      //parse database query 
      //update local database 
      
   //   db.put(text.getBytes(), text.getBytes())
     // println(db.toString())
      
      sender ! new TransformationJob(text.toUpperCase)
      
    case state: CurrentClusterState =>
      state.members.filter(_.status == MemberStatus.Up) foreach register
    case MemberUp(m) => register(m)
  }

  def register(member: Member): Unit =
    if (member.hasRole("frontend"))
      context.actorSelection(RootActorPath(member.address) / "user" / "frontend") !
        BackendRegistration
}
//#backend