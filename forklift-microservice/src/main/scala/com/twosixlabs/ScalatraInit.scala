package com.twosixlabs

import com.mchange.v2.c3p0.ComboPooledDataSource
import com.twosixlabs.dart.auth.controllers.SecureDartController
import com.twosixlabs.dart.auth.tenant.indices.InMemoryCorpusTenantIndex
import com.twosixlabs.dart.auth.tenant.{CorpusTenant, CorpusTenantIndex, GlobalCorpus}
import com.twosixlabs.dart.aws.S3Bucket
import com.twosixlabs.dart.forklift.controller.{ForkliftController, ForkliftControllerDependencies, ProcurementHandler}
import com.twosixlabs.dart.forklift.procurement.repositories.{LocalProcurementRepository, S3ProcurementRepository}
import com.twosixlabs.dart.forklift.procurement.ProcurementRepository
import com.twosixlabs.dart.operations.status.client.{PipelineStatusQueryClient, PipelineStatusUpdateClient, SqlPipelineStatusQueryClient, SqlPipelineStatusUpdateClient}
import com.twosixlabs.dart.rest.ApiStandards
import com.twosixlabs.dart.rest.scalatra.DartRootServlet
import com.twosixlabs.dart.sql.SqlClient
import com.twosixlabs.dart.status.readers.{DartStatusReader, DartStatusReaderDependencies}
import com.twosixlabs.dart.status.services.{CorpexDocIdService, CorpexDocIdServiceDependencies, DocIdsService}
import com.twosixlabs.dart.status.{OperationsStatusReader, OperationsStatusReaderDependencies, StatusReader, StatusReaderController, StatusReaderControllerDependencies}
import com.typesafe.config.ConfigFactory
import org.scalatra.LifeCycle
import software.amazon.awssdk.auth.credentials.{AwsCredentialsProvider, EnvironmentVariableCredentialsProvider, InstanceProfileCredentialsProvider, SystemPropertyCredentialsProvider}

import javax.servlet.ServletContext
import scala.collection.JavaConverters._
import scala.util.{Success, Try}

class ScalatraInit extends LifeCycle {

    private val props = System.getProperties.asScala

    private val tmpDir = System.getProperty( "java.io.tmpdir" )

    val allowedOrigins = props( "cors.allowed.origins" )

    private val procurementRepository : ProcurementRepository = {
        props( "persistence.mode" ) match {
            case "AWS" =>
                val bucket = {
                    val h = props( "persistence.bucket.name" )
                    if ( h == "_env_" ) System.getenv().get( "PERSISTENCE_BUCKET_NAME" )
                    else h
                }
                val credentials : AwsCredentialsProvider = {
                    Try {
                        val h = props( "credentials.provider" )
                        if ( h == "_env_" ) System.getenv().get( "CREDENTIALS_PROVIDER" )
                        else h
                    } match {
                        case Success( "INSTANCE" ) => InstanceProfileCredentialsProvider.create()
                        case Success( "ENVIRONMENT" ) => EnvironmentVariableCredentialsProvider.create()
                        case _ => SystemPropertyCredentialsProvider.create()
                    }
                }
                val s3Bucket = new S3Bucket( bucket, credentials, tmpDir )
                new S3ProcurementRepository( s3Bucket )
            case _ =>
                val persistenceDirPath = props( "persistence.dir" )
                new LocalProcurementRepository( persistenceDirPath )
        }
    }

    private val procurementHandler : ProcurementHandler = {
        new ProcurementHandler( procurementRepository )
    }

    private val numAnnos = props( "dart.num.annotators" ).toInt
    private val statusTable = props( "operations.db.table" )
    private val statusHost = props( "operations.db.host" )
    private val statusPort = props( "operations.db.port" ).toInt
    private val statusUser = props( "operations.db.user" )
    private val statusPassword = props( "operations.db.password" )
    private val statusDb = props( "operations.db.name" )

    val ds = new ComboPooledDataSource()
    ds.setDriverClass( "org.postgresql.Driver" )
    ds.setJdbcUrl( s"jdbc:postgresql://$statusHost:$statusPort/$statusDb" )
    ds.setUser( statusUser )
    ds.setPassword( statusPassword )
    ds.setMinPoolSize( 1 )
    ds.setAcquireIncrement( 1 )
    ds.setMaxPoolSize( 50 )

    private val sqlClient = new SqlClient( ds )
    private val opsQueryClient = new SqlPipelineStatusQueryClient( sqlClient, statusTable )
    private val inMemoryTenantIndex = new InMemoryCorpusTenantIndex( CorpusTenant( "baltics", GlobalCorpus ), CorpusTenant( "europe", GlobalCorpus ), CorpusTenant( "test3", GlobalCorpus ) )

    val typesafeConfig = ConfigFactory.defaultApplication().resolve()
    val authDeps = SecureDartController.authDeps( typesafeConfig )

    private val forkliftControllerDependencies = new ForkliftControllerDependencies {
        override val docHandler : ProcurementHandler = procurementHandler
        override val opsUpdateClient : PipelineStatusUpdateClient = new SqlPipelineStatusUpdateClient( sqlClient, statusTable )
        override val tenantIndex : CorpusTenantIndex = inMemoryTenantIndex
        override val secretKey : Option[ String ] = authDeps.secretKey
        override val useDartAuth : Boolean = authDeps.useDartAuth
        override val basicAuthCredentials: Seq[ (String, String) ] = authDeps.basicAuthCredentials
    }

    private val statusReaderService : OperationsStatusReader = new OperationsStatusReader( new OperationsStatusReaderDependencies {
        override val operationsClient : PipelineStatusQueryClient = opsQueryClient
        override val dartReader : DartStatusReader = new DartStatusReader( new DartStatusReaderDependencies {
            override val numAnnotators : Int = numAnnos
        } )
    } )

    private val corpexDocIdServiceDependencies = new CorpexDocIdServiceDependencies {
        override val corpexHost : String = props( "corpex.host" )
        override val corpexPort : Int = props( "corpex.port" ).toInt
        override val corpexSearchPath : String = "/dart/api/v1/corpex/search"
    }

    private val statusReaderControllerDependencies = new StatusReaderControllerDependencies {
        override val statusReader : StatusReader = statusReaderService
        override val docIdsService : DocIdsService = new CorpexDocIdService( corpexDocIdServiceDependencies )
    }

    val basePath : String = ApiStandards.DART_API_PREFIX_V1 + "/forklift"

    override def init( context : ServletContext ) : Unit = {
        context.setInitParameter( "org.scalatra.cors.allowedOrigins", allowedOrigins )
        context.mount( new DartRootServlet( Some( basePath ), Some( getClass.getPackage.getImplementationVersion ) ), "/*" )
        context.mount( new ForkliftController( forkliftControllerDependencies ), basePath + "/upload/*" )
        context.mount( new StatusReaderController( statusReaderControllerDependencies ), ApiStandards.DART_API_PREFIX_V1 + "/status/*" )
    }

    // Scalatra callback to close out resources
    override def destroy( context : ServletContext ) : Unit = {
        super.destroy( context )
    }

}
