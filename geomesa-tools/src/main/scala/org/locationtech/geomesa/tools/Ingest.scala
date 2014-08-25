/*
 * Copyright 2014 Commonwealth Computer Research, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.locationtech.geomesa.tools

import java.net.URLEncoder
import com.twitter.scalding.{Args, Hdfs, Mode}
import com.typesafe.scalalogging.slf4j.Logging
import org.apache.hadoop.conf.Configuration
import org.locationtech.geomesa.core.iterators.SpatioTemporalIntersectingIterator
import org.locationtech.geomesa.jobs.JobUtils
import org.locationtech.geomesa.tools.Utils.IngestParams

class Ingest() extends Logging with AccumuloProperties {

  def getAccumuloDataStoreConf(config: IngestArguments, password: String) = Map (
    "instanceId"        ->  instanceName,
    "zookeepers"        ->  zookeepers,
    "user"              ->  config.username,
    "password"          ->  password,
    "auths"             ->  config.auths.orNull,
    "visibilities"      ->  config.visibilities.orNull,
    "maxShard"          ->  Some(config.maxShards),
    "indexSchemaFormat" ->  config.indexSchemaFmt.orNull,
    "tableName"         ->  config.catalog
  )

  def defineIngestJob(config: IngestArguments, password: String) = {
    config.format.get.toUpperCase match {
      case "CSV" | "TSV" =>
        config.method.toLowerCase match {
          case "local" =>
            logger.info("Local Ingest has started, please wait.")
            runIngestJob(config, "--local", password)
          case "mr" =>
            logger.info("Map-reduced Ingest has started, please wait.")
            runIngestJob(config, "--hdfs", password)
          case _ =>
            logger.error("Error, no such ingest method for CSV or TSV found, no data ingested")
        }
      case "SHP" =>
        val dsConfig = getAccumuloDataStoreConf(config, password)
        ShpIngest.doIngest(config, dsConfig)
      case _ =>
        logger.error(s"Error: file format not supported." +
          s" Supported formats include: CSV, TSV, and SHP. No data ingested.")

    }
  }

  def runIngestJob(config: IngestArguments, fileSystem: String, password: String): Unit = {
    SpatioTemporalIntersectingIterator.initClassLoader(null)
    val libJars = JobUtils.getJarsFromClasspath(classOf[SVIngest]).mkString(",")
    logger.info(libJars)
    val conf = new Configuration()
    // not sure about this part
    val args = new collection.mutable.ListBuffer[String]()
    args.append(classOf[SVIngest].getCanonicalName)
    args.append(fileSystem)
    args.append("-libjars", libJars)
    args.append("--" + IngestParams.FILE_PATH, config.file)
    args.append("--" + IngestParams.SFT_SPEC, URLEncoder.encode(config.spec, "UTF-8"))
    args.append("--" + IngestParams.CATALOG_TABLE, config.catalog)
    args.append("--" + IngestParams.ZOOKEEPERS, zookeepers)
    args.append("--" + IngestParams.ACCUMULO_INSTANCE, instanceName)
    args.append("--" + IngestParams.ACCUMULO_USER, config.username)
    args.append("--" + IngestParams.ACCUMULO_PASSWORD, password)
    args.append("--" + IngestParams.SKIP_HEADER, config.skipHeader.toString)
    args.append("--" + IngestParams.DO_HASH, config.doHash.toString)
    // optional parameters
    if ( config.dtFormat.isDefined )        args.append("--" + IngestParams.DT_FORMAT, config.dtFormat.get)
    if ( config.idFields.isDefined )        args.append("--" + IngestParams.ID_FIELDS, config.idFields.get)
    if ( config.dtField.isDefined )         args.append("--" + IngestParams.DT_FIELD, config.dtField.get)
    if ( config.lonAttribute.isDefined )    args.append("--" + IngestParams.LON_ATTRIBUTE, config.lonAttribute.get)
    if ( config.latAttribute.isDefined )    args.append("--" + IngestParams.LAT_ATTRIBUTE, config.latAttribute.get)
    if ( config.format.isDefined )          args.append("--" + IngestParams.FORMAT, config.format.get)
    if ( config.featureName.isDefined )     args.append("--" + IngestParams.FEATURE_NAME, config.featureName.get)
    if ( config.auths.isDefined )           args.append("--" + IngestParams.AUTHORIZATIONS, config.auths.get)
    if ( config.visibilities.isDefined )    args.append("--" + IngestParams.VISIBILITIES, config.visibilities.get)
    if ( config.indexSchemaFmt.isDefined )  args.append("--" + IngestParams.INDEX_SCHEMA_FMT, config.indexSchemaFmt.get)
    if ( config.maxShards.isDefined )       args.append("--" + IngestParams.SHARDS, config.maxShards.get.toString)
    // since we are not in a test script we are choosing to run the ingest
    args.append("--" + IngestParams.RUN_INGEST, "true")

    val hdfsMode = if (fileSystem == "mr") Hdfs(strict=true, conf) else Hdfs(strict=false, conf)
    val arguments = Mode.putMode(hdfsMode, Args(args))
    val job = new SVIngest(arguments)
    val flow = job.buildFlow
    flow.complete()
  }
}


object Ingest extends App with Logging with GetPassword {
  val parser = new scopt.OptionParser[IngestArguments]("geomesa-tools ingest") {
    head("GeoMesa Tools Ingest", "1.0")
    opt[String]('u', "username") action { (x, c) =>
      c.copy(username = x) } text "Accumulo username" required()
    opt[String]('p', "password") action { (x, c) =>
      c.copy(password = x) } text "Accumulo password, This can also be provided after entering a command" optional()
    opt[String]('c', "catalog").action { (s, c) =>
      c.copy(catalog = s) } text "the name of the Accumulo table to use -- or create" required()
    opt[String]('a', "auths") action { (s, c) =>
      c.copy(auths = Option(s)) } text "Accumulo auths (optional)" optional()
    opt[String]('v', "visibilities") action { (s, c) =>
      c.copy(visibilities = Option(s)) } text "Accumulo visibilities (optional)" optional()
    opt[String]('i', "indexSchemaFormat") action { (s, c) =>
      c.copy(indexSchemaFmt = Option(s)) } text "Accumulo index schema format (optional)" optional()
    opt[Int]("shards") action { (i, c) =>
      c.copy(maxShards = Option(i)) } text "Accumulo number of shards to use (optional)" optional()
    opt[String]('f', "feature-name").action { (s, c) =>
      c.copy(featureName = Option(s)) } text "the name of the feature" required()
    opt[String]('s', "sftspec").action { (s, c) =>
      c.copy(spec = s) } text "the sft specification of the file," +
      " must match number of columns and order of ingest file if csv or tsv formatted." +
      " If ingesting lat/lon column data an additional field for the point geometry must be added, ie: *geom:Point ." optional()
    opt[String]("datetime").action { (s, c) =>
      c.copy(dtField = Option(s)) } text "the name of the datetime field in the sft" optional()
    opt[String]("dtformat").action { (s, c) =>
      c.copy(dtFormat = Option(s)) } text "the format of the datetime field" optional()
    opt[String]("idfields").action { (s, c) =>
      c.copy(idFields = Option(s)) } text "the set of attributes of each feature used" +
      " to encode the feature name" optional()
    opt[Unit]('h', "hash")action { (_, c) =>
      c.copy(doHash = true) } text "flag to md5 hash to identity of each feature" optional()
    opt[String]("lon").action { (s, c) =>
      c.copy(lonAttribute = Option(s)) } text "the name of the longitude field in the sft if ingesting point data" optional()
    opt[String]("lat").action { (s, c) =>
      c.copy(latAttribute = Option(s)) } text "the name of the latitude field in the sft if ingesting point data" optional()
    opt[Unit]("skip-header").action { (b, c) =>
      c.copy(skipHeader = true) } text "flag for skipping first line in file" optional()
    opt[String]("file").action { (s, c) =>
      c.copy(file = s, format = Option(getFileExtension(s)), method = getFileSystemMethod(s)) } text "the file to be ingested" required()
    help("help").text("show help command")
    checkConfig { c =>
      if (c.maxShards.isDefined && c.indexSchemaFmt.isDefined) {
        failure("Error: the options for setting the max shards and the indexSchemaFormat cannot both be set.")
      } else {
        success
      }
    }
  }

  try {
    parser.parse(args, IngestArguments()).map { config =>
      val pw = password(config.password)
      val ingest = new Ingest()
      ingest.defineIngestJob(config, pw)
    } getOrElse {
      logger.error("Error: command not recognized.")
    }
  }
  catch {
    case npe: NullPointerException => logger.error("Missing options and or unknown arguments on ingest." +
                                                   "\n\t See 'geomesa ingest --help'", npe)
  }

  def getFileExtension(file: String) = file.toLowerCase match {
    case csv if file.endsWith("csv") => "CSV"
    case tsv if file.endsWith("tsv") => "TSV"
    case shp if file.endsWith("shp") => "SHP"
    case _                           => "NOTSUPPORTED"
  }

  def getFileSystemMethod(path: String): String = path.toLowerCase.startsWith("hdfs") match {
    case true => "mr"
    case _    => "local"
  }

}

