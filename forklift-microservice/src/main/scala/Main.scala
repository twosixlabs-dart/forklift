
import com.twosixlabs.dart.commons.config.StandardCliConfig
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.webapp.WebAppContext
import org.scalatra.servlet.ScalatraListener
import org.slf4j.{Logger, LoggerFactory}

object Main extends StandardCliConfig {

  private val LOG: Logger = LoggerFactory.getLogger(getClass)

  def main( args : Array [ String ] ) : Unit = {

    loadEnvironment( args )

    val port = {
      val h = System.getProperty( "forklift.http.port" )
      if ( h == "_env_" ) System.getenv().get( "FORKLIFT_HTTP_PORT" )
      else h
    }.toInt

    val server = new Server ( port )
    val context = new WebAppContext()

    context.setContextPath( "/" )
    context.setResourceBase( "src/main/webapp" )
    context.setInitParameter( ScalatraListener.LifeCycleKey, "com.twosixlabs.ScalatraInit")
    context.addEventListener( new ScalatraListener )

    server.setHandler( context )
    server.start()
    server.join()
  }

}