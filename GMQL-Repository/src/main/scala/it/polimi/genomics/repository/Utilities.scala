package it.polimi.genomics.repository

/**
  * Created by abdulrahman on 12/04/16.
  */

import java.io.File

import org.slf4j.{Logger, LoggerFactory}

import scala.xml.XML

/**
  *
  * @author abdulrahman kaitoua <abdulrahman dot kaitoua at polimi dot it>
  */
class Utilities() {

  import Utilities.logger
  var USERNAME: String = System.getProperty("user.name")
  //USERNAME  = System.getenv("USER")
  val HDFS: String = "HDFS"
  val LOCAL: String = "LOCAL"

  var RepoDir: String = "/tmp/repo/data/"
  var HDFSRepoDir: String = System.getenv("GMQL_DFS_HOME")
  var MODE: String = System.getenv("GMQL_REPO_TYPE")
  var CoreConfigurationFiles: String = null
  var HDFSConfigurationFiles: String = null
  var HADOOP_CONF_DIR:String = "/usr/local/Cellar/hadoop/2.7.2/libexec/etc/hadoop/"
  var GMQLHOME: String = System.getenv("GMQL_LOCAL_HOME")
  var HADOOP_HOME:String = System.getenv("HADOOP_HOME")
  var GMQL_CONF_DIR:String = null


  /**
    *  Read Configurations from the system environment variables.
    *  The xml configurations will override any environment variables configurations.
    */
  def apply(confFile:String = "../conf") = {
    try {
      var file = new File(confFile+"/repo.xml")
      val xmlFile = XML.loadFile(file)
      val properties = (xmlFile \\ "property")
      //      val schemaType = (xmlFile \\ "gmqlSchema").head.attribute("type").get.head.text

      properties.map { x =>
        val att = x.attribute("name").get.head.text;
        val value = x.text;
        logger.debug(s"$att \t $value")
        att.toUpperCase() match {
          case Conf.GMQL_DFS_HOME => this.HDFSRepoDir = value;
          case Conf.GMQL_LOCAL_HOME => this.GMQLHOME = value;
          case Conf.GMQL_REPO_TYPE =>
            this.MODE = value.toUpperCase() match {
              case HDFS => HDFS;
              case LOCAL => LOCAL
              case _ => HDFS
            };
          case Conf.HADOOP_CONF_DIR  => HADOOP_CONF_DIR = value
          case Conf.HADOOP_HOME =>  HADOOP_HOME = value
          case Conf.GMQL_CONF_DIR => GMQL_CONF_DIR = value
          case _ => logger.error(s"Not known configuration property: $x, $value")
        }
        logger.debug(s"XML config override environment variables. $att = $value ")
      }
    } catch {
      case ex: Throwable => ex.printStackTrace(); logger.warn("XML config file is not found..")
    }

    HADOOP_CONF_DIR =  if(HADOOP_CONF_DIR == null) HADOOP_HOME+"/etc/hadoop/" else HADOOP_CONF_DIR
    CoreConfigurationFiles =  HADOOP_CONF_DIR+"/core-site.xml"
    HDFSConfigurationFiles = HADOOP_CONF_DIR+"/hdfs-site.xml"

    this.GMQLHOME =  if (this.GMQLHOME == null)  "/tmp/repo/" else  this.GMQLHOME

    GMQL_CONF_DIR =  if (!(this.GMQL_CONF_DIR == null))  GMQL_CONF_DIR else confFile

    this.USERNAME  = if (this.USERNAME  == null) "gmql_user" else this.USERNAME

    this.HDFSRepoDir = if (this.HDFSRepoDir == null)   "/user/repo/" else this.HDFSRepoDir

    this.MODE = if (this.MODE == null) {
      logger.error("Environment variable GMQL_REPO_TYPE is empty... execution set to LOCAL")
      this.HDFS
    } else this.MODE

    RepoDir = this.GMQLHOME + "/data/"

//    CoreConfigurationFiles = if (CoreConfigurationFiles == null) "/usr/local/Cellar/hadoop/2.7.2/libexec/etc/hadoop/core-site.xml" else CoreConfigurationFiles
//    HDFSConfigurationFiles = if (HDFSConfigurationFiles == null) "/usr/local/Cellar/hadoop/2.7.2/libexec/etc/hadoop/hdfs-site.xml" else HDFSConfigurationFiles
    logger.debug(CoreConfigurationFiles)
    logger.debug(HDFSConfigurationFiles)
    logger.debug("GMQL_LOCAL_HOME is set to = " +  this.GMQLHOME)
    logger.debug("GMQL_DFS_HOME, HDFS Repository is set to = " +  this.HDFSRepoDir)
    logger.debug("MODE is set to = " +  this.MODE)
    logger.debug("User is set to = " +  this.USERNAME)
  }

  /**
    *
    * Constract the Directory to the tmp folder
    *
    * @param userName [[ String]] of the user name
    * @return Directory location of the temp folder
    */
  def getTempDir(userName: String = USERNAME): String = GMQLHOME + "/tmp/" + userName + "/"

  /**
    *
    * Constract the Directory to the regions folder on HDFS
    *
    * @param userName [[ String]] of the user name
    * @return Directory location of the regions folder in HDFS
    */
  def getHDFSRegionDir(userName: String = USERNAME): String = HDFSRepoDir + userName + "/regions/"

  /**
    *
    * Constract the Directory to the regions folder on Local file system
    *
    * @param userName [[ String]] of the user name
    * @return Directory location of the regions folder in Local File system
    */
  def getRegionDir(userName: String = USERNAME): String = RepoDir + userName + "/regions/"

  /**
    *
    * Constract the Directory to the dataset folder
    *
    * @param userName [[ String]] of the user name
    * @return Directory location of the datasets folder
    */
  def getDataSetsDir(userName: String = USERNAME): String = RepoDir + userName + "/datasets/"

  /**
    *
    * Constract the Directory to the schema folder
    *
    * @param userName [[ String]] of the user name
    * @return Directory location of the schema folder
    */
  def getSchemaDir(userName: String = USERNAME): String = RepoDir + userName + "/schema/"

  /**
    *
    * Constract the Directory to the metadata folder
    *
    * @param userName [[ String]] of the user name
    * @return Directory location of the metadata folder
    */
  def getMetaDir(userName: String = USERNAME): String = RepoDir + userName + "/metadata/"

  /**
    *
    *  Constract the Directory to the indexes folder
    *
    * @param userName [[ String]] of the user name
    * @return Directory location of the indexes folder
    */
  def getIndexDir(userName: String = USERNAME): String = RepoDir + userName + "/indexes/"

  /**
    *
    * Constract the Directory to the GMQL scripts folder
    *
    * @param userName [[ String]] of the user name
    * @return Directory location of the GMQL scripts history folder
    */
  def getScriptsDir(userName: String = USERNAME): String = RepoDir + userName + "/queries/"

  /**
    *
    * Constract the Directory to the GMQL Repository folder for Specific user
    *
    * @param userName [[ String]] of the user name
    * @return Directory location of the repository user's folder
    */
  def getUserDir(userName: String = USERNAME): String = RepoDir + userName + "/"

  /**
    *
    * Constract the Directory to the Log folder
    *
    * @param userName [[ String]] of the user name
    * @return Directory location of the logs folder
    */
  def getLogDir(userName: String = USERNAME) = RepoDir + userName + "/logs/"

  /**
    *
    * Constract the Directory to the Configurations folder
    *
    * @return Directory location of the conf folder
    */
  def getConfDir() =  GMQL_CONF_DIR

  /**
    *
    * Set the home directory for GMQL.
    *
    * @param gmqlHome String of the path to the location of the home directory
    */
  def setGMQLHome(gmqlHome:String):Unit = {
    GMQLHOME = gmqlHome
  }

  /**
    * Set the configurations Directory for GMQL.
    *
    * @param confDir String of the location of the configuration directory
    */
  def setConfDir(confDir:String) = {
    GMQL_CONF_DIR = confDir
  }
}

object Utilities {
  private var instance: Utilities = null
  var confFolder: String = "../conf/"
  val logger: Logger = LoggerFactory.getLogger(Utilities.getClass)

  def apply(): Utilities = {
    if (instance == null) {
      instance = new Utilities();
      instance.apply(confFolder)
    }
    instance
  }
}

/**
  *  Configurations of GMQL
  */
object Conf {
  val GMQL_LOCAL_HOME = "GMQL_LOCAL_HOME"
  val GMQL_REPO_TYPE = "GMQL_REPO_TYPE"
  val GMQL_DFS_HOME = "GMQL_DFS_HOME";
  val HADOOP_HOME = "HADOOP_HOME";
  val HADOOP_CONF_DIR = "HADOOP_CONF_DIR"
  val GMQL_CONF_DIR = "GMQL_CONF_DIR"
}

