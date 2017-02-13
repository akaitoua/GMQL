package it.polimi.genomics.manager.Launchers

/**
  * @AUTHOR ABDULRAHMAN KAITOUA
  */

import java.io.{ByteArrayOutputStream, IOException, ObjectOutputStream}

import com.sun.jersey.core.util.Base64
import it.polimi.genomics.compiler.Operator
import it.polimi.genomics.core.DataStructures.IRDataSet
import it.polimi.genomics.core.ParsingType.PARSING_TYPE
import it.polimi.genomics.manager.GMQLJob
import org.apache.spark.launcher.{SparkAppHandle, SparkLauncher}

import scala.collection.JavaConverters._
import scala.util.Random
import it.polimi.genomics.repository.{Utilities => General_Utilities}
import it.polimi.genomics.repository.FSRepository.{LFSRepository, FS_Utilities => FSR_Utilities}

/**
  *  Set the configurations for spark launcher to lanch GMQL CLI with arguments
  *
  * @param sparkHome
  * @param hadoopConfDir
  * @param yarnConfDir
  * @param GMQLHOME
  * @param someCustomSetting
  * @param scriptPath
  * @param jobid
  * @param username
  */
class GMQLSparkSubmit(job:GMQLJob) {

  val SPARK_HOME = System.getenv("SPARK_HOME")
  val HADOOP_CONF_DIR = System.getenv("HADOOP_CONF_DIR")
  val YARN_CONF_DIR = System.getenv("YARN_CONF_DIR")
  val GMQL_HOME = System.getenv("GMQL_HOME")

  final val GMQLjar = GMQL_HOME + "/utils/lib/GMQL-Cli-2.0-jar-with-dependencies.jar"
  final val MASTER_CLASS = "it.polimi.genomics.cli.GMQLExecuteCommand"
  final val APPID = "GMQL_" + Random.nextInt() + "_" + job.jobId
  final val DRIVER_MEM = "10g"
  final val EXECUTOR_MEM = "4g"
  final val NUM_EXECUTORS = "15"
  final val CORES = "30"
  final val DEFAULT_PARALLELISM = "200"

  /**
    * Run GMQL Spark Job using Spark Launcher (client of Spark launcher server)
    * @return
    */
  def runSparkJob(): SparkAppHandle = {
    val env = Map(
      "HADOOP_CONF_DIR" -> HADOOP_CONF_DIR,
      "YARN_CONF_DIR" -> YARN_CONF_DIR
    )

    new SparkLauncher(env.asJava)
      .setSparkHome(SPARK_HOME)
      .setAppResource(GMQLjar)
      .setMainClass(MASTER_CLASS)
      .addAppArgs("-username", job.username,
        "-script", job.script.script/*serializeDAG(job.operators)*/,
        "-scriptpath", job.script.scriptPath,
        "-inputDirs",job.inputDataSets.map(x => x._1+":::"+x._2+"/").mkString(","),
        //TODO: Check how to get the schema path from the repository manager.
        "-schemata",job.inputDataSets.map(x => x._2+":::"+getSchema(job,x._1)).mkString(","),
        "-jobid", job.jobId,
        "-outputFormat",job.gMQLContext.outputFormat.toString,
        "-logDir",General_Utilities().getLogDir(job.username))
      .setConf("spark.app.id", APPID)
//      .setConf("spark.driver.memory", DRIVER_MEM)
//      .setConf("spark.akka.frameSize", "200")
//      .setConf("spark.executor.memory", EXECUTOR_MEM)
//      .setConf("spark.executor.instances", NUM_EXECUTORS)
//      .setConf("spark.executor.cores", CORES)
//      .setConf("spark.default.parallelism", DEFAULT_PARALLELISM)
//      .setConf("spark.driver.allowMultipleContexts", "true")
//      .setConf("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
//      .setConf("spark.kryoserializer.buffer", "64")
//      .setConf("spark.rdd.compress","true")
//      .setConf("spark.akka.threads","8")
//        .setConf("spark.yarn.am.memory","4g") // instead of driver.mem when yarn client mode
//        .setConf("spark.yarn.am.memoryOverhead","600") // instead of spark.yarn.driver.memoryOverhead when client mode
//        .setConf("spark.yarn.executor.memoryOverhead","600")
      .setVerbose(true)
      .startApplication()
  }

  /**
    * reading the data set schema to be sent along with the 
    * @param job
    * @param DS
    * @return
    */
  def getSchema(job:GMQLJob,DS:String):String = {
    import scala.io.Source
    import scala.collection.JavaConverters._

    val repository = new LFSRepository()
    import scala.collection.JavaConverters._
    val ds = new IRDataSet(DS, List[(String,PARSING_TYPE)]().asJava)
    val user = if(repository.DSExistsInPublic(ds))"public" else job.username
    Source.fromFile(General_Utilities().getSchemaDir(user)+DS+".schema").getLines().mkString("")
  }

  /**
    * Serialize GMQL DAG
    *
    * TODO: DAG serialization is Not used currently, instead we are sending the script as a parameter
    * @param dag input as a List of {@link Operator}
    * @return String as the serialization of the DAG
    */
  def serializeDAG(dag: List[Operator]): String = {
    try {
      val mylist =  new java.util.ArrayList[Operator]
      for(i <- dag) mylist.add(i)

      val byteArrayOutputStream = new ByteArrayOutputStream();
      val objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
      objectOutputStream.writeObject(mylist);
      objectOutputStream.close();
      new String(Base64.encode(byteArrayOutputStream.toByteArray()));

    } catch {
      case io: IOException => io.printStackTrace(); "none"
    }
  }
}