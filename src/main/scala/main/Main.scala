package main
import scala.concurrent.duration._
import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.RootActorPath
import akka.actor.Terminated
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.CurrentClusterState
import akka.cluster.ClusterEvent.MemberUp
import akka.cluster.Member
import akka.cluster.MemberStatus
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import akka.io.IO
import akka.pattern.ask
import spray.can.Http
import spray.util._
import spray.http._
import akka.event._
import spray.http.HttpMethods._
import spray.http.MediaTypes._
import scala.concurrent.Future
import akka.actor.ActorSelection.toScala
import akka.actor.actorRef2Scala
import akka.cluster.ClusterEvent.MemberUp
import spray.http.ContentType.apply
import scala.io.Source

//#imports

//#messages
case class TransformationJob(text: String)
case class TransformationResult(text: String)
case class JobFailed(reason: String, job: TransformationJob)
case object BackendRegistration
//#messages



object TransformationFrontend {
  def main(args: Array[String]): Unit = {

    // Override the configuration of the port when specified as program argument
    val config =
      (if (args.nonEmpty) ConfigFactory.parseString(s"akka.remote.netty.tcp.port=${args(0)}")
      else ConfigFactory.empty).withFallback(
        ConfigFactory.parseString("akka.cluster.roles = [frontend]")).
        withFallback(ConfigFactory.load())

    val system = ActorSystem("ClusterSystem", config)
    val frontend = system.actorOf(Props[TransformationFrontend], name = "frontend")
    implicit val actSystem = ActorSystem()
    IO(Http) ! Http.Bind(frontend, interface = "localhost", port = 8080)
  
  }
}

//#frontend
class TransformationFrontend extends Actor with SprayActorLogging {
  import context.dispatcher

  var backends = IndexedSeq.empty[ActorRef]
  var jobCounter = 0
  val duration = 30 seconds
  implicit val timeout = Timeout(duration)

   //Naive round robin routing
  var currentWorker = -1
  def nextWorker = {
    if (currentWorker >= backends.size - 1) {
      currentWorker = 0
    } else {
      currentWorker = currentWorker + 1
    }
    backends(currentWorker)
  }

  def index(s: Int) = {  
	/*
    HttpResponse(
    entity = HttpEntity(`text/html`,
    		Source.fromFile("index.html").mkString
      ) 
  )
  */ 
  HttpResponse(
    entity = HttpEntity(`text/html`,
      <html>
        <body>
          <h1>Akka Cluster!</h1>
          <p>{s} workers available</p>
        </body>
      </html>.toString()
      ) 
  )
  
  
  }

  
  
  
  
  lazy val noWorkers = HttpResponse(
    entity = HttpEntity(`text/html`,
      <html>
        <body>
          <h1>Akka Cluster!</h1>
          <p>No workers available</p>
        </body>
      </html>.toString()
    )
  )

  def jobResponse(s: String) = HttpResponse(
    entity = HttpEntity(`text/html`,
      <html>
        <body>
          <h1>Akka Cluster!</h1>
          <p>Response: {s}</p>
        </body>
      </html>.toString()
    )
  )

  def lightSpeedResponse(s: String) = HttpResponse(
    entity = HttpEntity(`text/html`,
      <html>
        <body>
          <h1><img src="http://corydobson.com/media/work/lightspeedLogo.jpg" style = "max-height:300px"></img>LightSpeed!</h1>
          <p>Response: {s}</p>
        </body>
      </html>.toString()
    )
  )
  
  

  context.actorOf(Props[TransformationBackend], name = "backend")

  def receive = {
    
    case _: Http.Connected => sender ! Http.Register(self)

    case HttpRequest(GET, Uri.Path("/"), _, _, _) =>
      sender ! index(backends.size)
     
      
      
    case HttpRequest(GET, Uri.Path("/lightspeed"), _, _, _) =>  
      jobCounter += 1
      val future : Future[TransformationJob] = (nextWorker ? new TransformationJob(jobCounter + "-job")).mapTo[TransformationJob]
      val originalSender = sender
      
      future onSuccess {
        case TransformationJob(text) =>
            	 originalSender ! lightSpeedResponse(text)      
      }  
    
      /*    
     case HttpRequest(POST, Uri.Path("/lightspeed"), _, _body, _) =>  
      jobCounter += 1
      val future : Future[TransformationJob] = (nextWorker ? new TransformationJob(jobCounter + "-job")).mapTo[TransformationJob]
      val originalSender = sender
      val source = "{'name': 'testing'}"
        
      val jsonAsst = HttpResponse(entity= HttpEntity(`application/json`, JsonParser(source).toString()))
      
      future onSuccess {
        case TransformationJob(text) =>
            	 originalSender ! lightSpeedResponse(text)      
      }  
        */
      
    case HttpRequest(GET, Uri.Path("/lightspeed/put"), _, _, _) =>   
      
       val future : Future[TransformationJob] = (nextWorker ? new TransformationJob(jobCounter + "-job")).mapTo[TransformationJob]
      val originalSender = sender
      future onSuccess {
        case TransformationJob(text) =>
            	 originalSender ! jobResponse(text)      
      }  
      
      
    case HttpRequest(GET, Uri.Path("/work"), _, _, _) =>
      jobCounter += 5
      val future : Future[TransformationJob] = (nextWorker ? new TransformationJob(jobCounter + "-job")).mapTo[TransformationJob]
      val originalSender = sender
      future onSuccess {
        case TransformationJob(text) =>
      
          originalSender ! jobResponse(text)  
          
      }
      
    case BackendRegistration if !backends.contains(sender) =>
      context watch sender
      backends = backends :+ sender

    case Terminated(a) =>
      backends = backends.filterNot(_ == a)
   
  }
}
//#frontend
