package it.polimi.genomics.importer.ENCODEImporter

import java.io.File
import java.net.URL
import java.util.Calendar

import it.polimi.genomics.importer.GMQLImporter.{GMQLDataset, GMQLDownloader, GMQLSource}
import it.polimi.genomics.importer.FileLogger.FileLogger
import org.slf4j.LoggerFactory

import scala.collection.immutable.Seq
import scala.sys.process._
import scala.io.Source
import scala.xml.{Elem, XML}

/**
  * Created by Nacho on 10/13/16.
  */
class ENCODEDownloader extends GMQLDownloader {
  val logger = LoggerFactory.getLogger(this.getClass)

  /**
    * downloads the files from the source defined in the loader
    * into the folder defined in the loader
    * recursively checks all folders and subfolders matching with the regular expressions defined in the loader
    *
    * @param source contains specific download and sorting info.
    */
  override def download(source: GMQLSource): Unit = {
    logger.info("Starting download for: " + source.outputFolder)
    if (!new java.io.File(source.outputFolder).exists) {
      logger.debug("file " + source.outputFolder + " created")
      new java.io.File(source.outputFolder).mkdirs()
    }
    downloadIndexAndMeta(source)
  }

  /**
    * For ENCODE given the parameters, a link for downloading metadata and
    * file index is generated, here, that file is downloaded and then, for everyone
    * downloads all the files linked by it.
    *
    * @param source information needed for downloading ENCODE datasets.
    */
  private def downloadIndexAndMeta(source: GMQLSource): Unit = {
    source.datasets.foreach(dataset => {
      if(dataset.downloadEnabled) {
        val outputPath = source.outputFolder + File.separator + dataset.outputFolder + File.separator + "Downloads"
        if (!new java.io.File(outputPath).exists) {
          new java.io.File(outputPath).mkdirs()
        }
        val indexAndMetaUrl = generateDownloadIndexAndMetaUrl(source, dataset)
        val reportUrl = generateReportUrl(source, dataset)
        //here I should log the .meta file with FileLogger but I dont know how to know the
        //parameters needed prior to download the file. also, this file should always be downloaded,
        //so there is no need of checking it, ENCODE always provide the last version of this .meta file.
        if (urlExists(indexAndMetaUrl)) {
          val log = new FileLogger(outputPath)
          /*I will check all the server files against the local ones so i mark as to compare,
            * the files will change their state while I check each one of them. If there is
            * a file deleted from the server will be marked as OUTDATED before saving the table back*/
          log.markToCompare()
          downloadFileFromURL(
            indexAndMetaUrl,
            outputPath + File.separator + "metadata" + ".tsv")
          log.checkIfUpdate(
            "metadata.tsv",
            indexAndMetaUrl,
            new File(outputPath + File.separator + "metadata" + ".tsv").getTotalSpace.toString,
            Calendar.getInstance.getTime.toString)
          log.markAsUpdated("metadata.tsv")

          downloadFileFromURL(
            reportUrl,
            outputPath + File.separator + "report" + ".tsv")
          log.checkIfUpdate(
            "report.tsv",
            reportUrl,
            new File(outputPath + File.separator + "report" + ".tsv").getTotalSpace.toString,
            Calendar.getInstance.getTime.toString)
          log.markAsUpdated("report.tsv")

          log.saveTable()
          downloadFilesFromMetadataFile(source, dataset)
          logger.info("download for " + dataset.outputFolder + " completed")
        }
        else {
          logger.error("download link generated by " + dataset.outputFolder + " does not exist")
          logger.debug("download link:" + indexAndMetaUrl)
        }
      }
    })
  }

  /**
    * generates download link for the metadata file
    *
    * @param source  contains information related for connecting to ENCODE
    * @param dataset contains information for parameters of the url
    * @return full url to download metadata file from encode.
    */
  def generateDownloadIndexAndMetaUrl(source: GMQLSource, dataset: GMQLDataset): String = {
    source.url + source.parameters.filter(_._1.equalsIgnoreCase("metadata_prefix")).head._2 + generateParameterSet(dataset) + source.parameters.filter(_._1 == "metadata_suffix").head._2
  }
  def generateReportUrl(source: GMQLSource, dataset: GMQLDataset): String ={
    source.url + source.parameters.filter(_._1.equalsIgnoreCase("report_prefix")).head._2 + generateParameterSet(dataset) + "&" + generateFieldSet(source)
  }
  def generateFieldSet(source: GMQLSource): String ={
    val file: Elem = XML.loadFile(source.parameters.filter(_._1.equalsIgnoreCase("encode_metadata_configuration")).head._2)
    var set = ""
    ((file\\"encode_metadata_config"\"parameter_list").filter(list =>
      (list\"@name").text.equalsIgnoreCase("encode_report_tsv"))\"parameter").filter(field =>
      (field\"@include").text.equalsIgnoreCase("true") && (field\"key").text.equalsIgnoreCase("field")).foreach(field =>{
      set = set + (field\\"key").text + "=" + (field\\"value").text+"&"
    })
    if (set.endsWith("&"))
      set.substring(0, set.length - 1)
    else
      set
  }
  /**
    * concatenates all the folder's parameters with & in between them
    * and = inside them
    *
    * @param dataset contains all the parameters information
    * @return string with parameter=value & ....
    */
  private def generateParameterSet(dataset: GMQLDataset): String = {
    var set = ""
    dataset.parameters.foreach(parameter => {
      set = set + parameter._1 + "=" + parameter._2 + "&"
    })
    if (set.endsWith("&"))
      set.substring(0, set.length - 1)
    else
      set
  }

  /**
    * given a url and destination path, downloads that file into the path
    *
    * @param url  source file url.
    * @param path destination file path and name.
    */
  def downloadFileFromURL(url: String, path: String): Unit = {
    //logger.debug("Downloading: " + path + " from: " + url)
    //I have to recheck if this is safe
    new URL(url) #> new File(path) !!;
    logger.info("Downloading: " + path + " from: " + url + " DONE")
  }

  /**
    * explores the downloaded metadata file with all the urls directing to the files to download,
    * checks if the files have to be updated, downloaded, deleted and performs the actions needed.
    * puts all downloaded files into /information.outputFolder/folder.outputFolder/Downloads
    *
    * @param source  contains information for ENCODE download.
    * @param dataset dataset specific information about its location.
    */
  private def downloadFilesFromMetadataFile(source: GMQLSource, dataset: GMQLDataset): Unit = {
    //attributes that im looking into the line:
    //Experiment date released (22), Size (36), md5sum (38), File download URL(39)
    //maybe this parameters should be entered by xml file
    val path =source.outputFolder + File.separator + dataset.outputFolder + File.separator + "Downloads"
    val header = Source.fromFile(path + File.separator + "metadata" + ".tsv").getLines().next().split("\t")

    val originLastUpdate = header.lastIndexOf("Experiment date released")
    val originSize = header.lastIndexOf("Size")
    //to be used
    //val md5sum = header.lastIndexOf("md5sum")
    val url = header.lastIndexOf("File download URL")


    val log = new FileLogger(path)
    Source.fromFile(path + File.separator + "metadata.tsv").getLines().drop(1).foreach(line => {
      val fields = line.split("\t")
      val filename = fields(url).split(File.separator).last
      if (urlExists(fields(url))) {
        if (log.checkIfUpdate(filename, fields(url), fields(originSize), fields(originLastUpdate))) {
          //MUST BE DONE: handle if the file is not downloaded.
          //if not downloaded use log.markAsFailed(filename)
          downloadFileFromURL(fields(url), path + File.separator + filename)
          log.markAsUpdated(filename)
        }
      }
      else
        logger.error("could not download " + filename + " from " + dataset.outputFolder)
    })
    log.markAsOutdated()
    log.saveTable()
  }

  /**
    * checks if the given URL exists
    *
    * @param path URL to check
    * @return URL exists
    */
  def urlExists(path: String): Boolean = {
    try {
      scala.io.Source.fromURL(path)
      true
    } catch {
      case _: Throwable => false
    }
  }
}
