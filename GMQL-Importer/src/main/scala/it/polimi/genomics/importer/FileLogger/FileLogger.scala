package it.polimi.genomics.importer.FileLogger

import java.util.Calendar

import org.slf4j.LoggerFactory

import scala.xml.XML
/**
  * Created by Nacho on 16/09/2016.
  */
/**
  * keeps a log file on the working folder called FileLog.xml
  * Log organization follows FileLogElement structure
  *
  * How to use it properly:
  *   1- Instantiate the class by passing the objective dataset folder (path/Downloads or path/Transformations).
  *   2- Set the dataset into comparision mode by using markToCompare() method.
  *   3- For each file, the FileLogger will tell you if needs using checkIfUpdate(...) method.
  *     3.1- After you update the file (if you have to) use markAsUpdated(...) method to indicate that was updated.
  *     3.2- If the update process was not correct, use markAsFailed(...) method to indicate that was an error.
  *   4- After modifications have been done, use method markAsOutdated() to log which files are not kept in the source.
  *   5- You can get lists of files to be updated (filesToUpdate()) and outdated (filesToOutdate()).
  *   6- After you use the information in the log (as example, when the transformer uses info generated by downloader)
  *       log it by using the method markAsProcessed().
  *   7- If you instantiate the logger more than one time, use method saveTable() to update the FileLog.xml file and
  *       keep track of the actions you have performed.
  *
  * @param path working folder.
  */
class FileLogger(path: String){
  val logger = LoggerFactory.getLogger(this.getClass)

  if (!new java.io.File(path).exists) {
    new java.io.File(path).mkdirs()
  }
  if (!new java.io.File(path+ "/" + "FileLog.xml").exists()) {
    val elem = <file_list></file_list>
    XML.save(path + "/" + "FileLog.xml", elem)
  }
  var files: List[FileLogElement] = (XML.loadFile(path+ "/" + "FileLog.xml")\\"file").map(file =>
    FileLogElement(
      (file\"name").text,
      (file\"last_update").text,
      FILE_STATUS.withName((file\"status").text),
      (file\"origin").text,
      (file\"origin_size").text,
      (file\"origin_last_update").text,
      (file\"date_processed").text)).toList

  /**
    * checking with the log file decides if the file has to be updated/added or not.
    *
    * @param filename file name
    * @param origin origin file from the server
    * @param originSize file size from the server
    * @param originLastUpdate last modification for the file on the server
    * @return file has to be updated/added
    */
  def checkIfUpdate(filename: String, origin:String,originSize: String, originLastUpdate: String): Boolean= {
    //if the file already exists
    if (files.exists(_.name == filename)) {
      val oldFile = files.filter(_.name == filename).head
      //here I compare if size or modification date are different, but should compare hashs
      //also I check if the file already has an UPDATE or ADD status.
      if (oldFile.status == FILE_STATUS.UPDATE ||
        oldFile.status == FILE_STATUS.ADD ||
        oldFile.status == FILE_STATUS.FAILED ||
        oldFile.status == FILE_STATUS.OUTDATED)
        true
      else if(oldFile.originSize != originSize ||
        oldFile.originLastUpdate != originLastUpdate)
        true
      else {
        //while comparing the files, if the file is ok and does not have to be changed or deleted, is set to NOTHING.
        files = files.map(file =>
          if (file.name == oldFile.name && file.status == FILE_STATUS.COMPARE)
            FileLogElement(
              oldFile.name,
              oldFile.lastUpdate,
              FILE_STATUS.NOTHING,
              oldFile.origin,
              oldFile.originSize,
              oldFile.originLastUpdate,
              oldFile.dateProcessed)
          else file)
        false
      }
    }
    //is a new file, i have to add it to the log
    else {
      logger.debug(filename + "added to the log " + path)
      //I initiate the status as FAILED to indicate the file has not been downloaded, then should be marked as update
      files = files :+ FileLogElement(filename, "", FILE_STATUS.FAILED, origin, originSize, originLastUpdate, "")
      true
    }
  }


  /**
    * returns a list with all the files to be updated by the next process.
    * returns files with status ADD or UPDATE
    * @return list with files that should be updated.
    */
  def filesToUpdate(): List[FileLogElement] ={
    files.filter(file => file.status == FILE_STATUS.ADD || file.status == FILE_STATUS.UPDATE)
  }
  /**
    * returns a list with all the files to be outdated by the next process.
    * returns files with status OUTDATE
    * @return list with files that should be outdated.
    */
  def filesToOutdate(): List[FileLogElement] ={
    files.filter(file => file.status == FILE_STATUS.ADD || file.status == FILE_STATUS.UPDATE)
  }
  /**
    * saves the full log into the FileLog.xml.
    */
  def saveTable(): Unit = {
    val log =
      <file_list>
        {files.flatMap(file =>{<file>
            {<name>{file.name}</name>}
            {<last_update>{file.lastUpdate}</last_update>}
            {<status>{file.status}</status>}
            {<origin>{file.origin}</origin>}
            {<origin_size>{file.originSize}</origin_size>}
            {<origin_last_update>{file.originLastUpdate}</origin_last_update>}
            {<date_processed>{file.dateProcessed}</date_processed>}
        </file>}
      )}
      </file_list>
    XML.save(path + "/" + "FileLog.xml", log)
    logger.debug("log saved in "+ path + "with "+ files.size + "files.")
  }


  /**
    * marks indicated file as to be ADD or UPDATED
    * @param filename name of the file to be marked
    */
  def markAsUpdated(filename:String): Unit = {
    files = files.map(file =>
      if (file.name == filename)
        FileLogElement(
          file.name,
          if (file.status == FILE_STATUS.UPDATE || file.status == FILE_STATUS.ADD)
            file.lastUpdate
          else Calendar.getInstance.getTime.toString,
          if (file.lastUpdate == "") FILE_STATUS.ADD else FILE_STATUS.UPDATE,
          file.origin,
          file.originSize,
          file.originLastUpdate,
          file.dateProcessed)
      else file)
  }
  /**
    * to be used when the file download or transformation fails, puts file status into FAILED
    * @param filename name of the file to be marked
    */
  def markAsFailed(filename:String): Unit = {
    files = files.map(file =>
      if (file.name == filename)
        FileLogElement(
          file.name,
          Calendar.getInstance.getTime.toString,
          FILE_STATUS.FAILED,
          file.origin,
          file.originSize,
          file.originLastUpdate,
          file.dateProcessed)
      else file)
  }
  /**
    * mark all files that have been compared into the log as outdated.
    * meant to be used at the end of all comparisons (all check if udpate)
    * changes COMPARE to OUTDATED.
    */
  def markAsOutdated(): Unit ={
    files = files.map(file =>
      if(file.status != FILE_STATUS.COMPARE) file
      else FileLogElement(
        file.name,
        file.lastUpdate,
        FILE_STATUS.OUTDATED,
        file.origin,
        file.originSize,
        file.originLastUpdate,
        file.dateProcessed))
  }
  /**
    * mark all the files with status NOTHING into status COMPARE
    * meant to be used to check which files have been deleted from the source.
    */
  def markToCompare(): Unit ={
    files = files.map(file =>
      if(file.status == FILE_STATUS.UPDATE || file.status == FILE_STATUS.ADD) file
      else FileLogElement(
        file.name,
        file.lastUpdate,
        FILE_STATUS.COMPARE,
        file.origin,
        file.originSize,
        file.originLastUpdate,
        file.dateProcessed))
  }

  /**
    * updates the status of the files in the log in order to inform that was already processed.
    * turns "ADD" and "UPDATE" into "NOTHING"
    * updates dateProcessed
    */
  def markAsProcessed(): Unit ={
    files = files.map(file =>
      if(file.status == FILE_STATUS.ADD || file.status == FILE_STATUS.UPDATE)
        FileLogElement(file.name,
          file.lastUpdate,
          FILE_STATUS.NOTHING,
          file.origin,
          file.originSize,
          file.originLastUpdate,
          Calendar.getInstance.getTime.toString)
      else file)
  }
}



