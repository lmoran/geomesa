/*
 * Copyright 2014 Commonwealth Computer Research, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an AS IS BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.locationtech.geomesa.jobs

import com.twitter.scalding.{Hdfs, Read, Write}
import org.apache.accumulo.core.client.mock.MockInstance
import org.apache.accumulo.core.client.security.tokens.PasswordToken
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.mapred.JobConf
import org.junit.runner.RunWith
import org.locationtech.geomesa.jobs.scalding._
import org.locationtech.geomesa.jobs.scalding.taps.{AccumuloScheme, AccumuloTap}
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class AccumuloSourceTest extends Specification {

  val instance = new MockInstance("accumulo-source-test")
  val connector = instance.getConnector("user", new PasswordToken("pwd"))
  Seq("table_in", "table_out").foreach(t => connector.tableOperations().create(t))

  val input = AccumuloInputOptions(instance.getInstanceName,
                                   instance.getZooKeepers,
                                   "user",
                                   "pwd",
                                   "table_in")
  val output = AccumuloOutputOptions(instance.getInstanceName,
                                     instance.getZooKeepers,
                                     "user",
                                     "pwd",
                                     "table_out")

  "AccumuloSource" should {
    "create read and write taps" in {
      implicit val mode = Hdfs(true, new Configuration())
      val readTap = AccumuloSource(input).createTap(Read)
      val writeTap = AccumuloSource(output).createTap(Write)
      readTap must haveClass[AccumuloTap]
      writeTap must haveClass[AccumuloTap]
      readTap.getIdentifier mustNotEqual(writeTap.getIdentifier)
    }
  }

  "AccumuloTap" should {
    "create tables and check their existence" in {
      skipped("this doesn't work with mock accumulo - revisit if we start using mini accumulo")

      val inScheme = new AccumuloScheme(input.copy(table = "test_create_in"))
      val outScheme = new AccumuloScheme(output.copy(table = "test_create_out"))

      val conf = new JobConf()
      val readTap = new AccumuloTap(Read, inScheme)
      readTap.resourceExists(conf) mustEqual(false)
      readTap.createResource(conf)
      readTap.resourceExists(conf) mustEqual(true)
      connector.tableOperations().exists("test_create_in") mustEqual(true)

      val writeTap = new AccumuloTap(Write, outScheme)
      writeTap.resourceExists(conf) mustEqual(false)
      writeTap.createResource(conf)
      writeTap.resourceExists(conf) mustEqual(true)
      connector.tableOperations().exists("test_create_out") mustEqual(true)
    }
  }

}
