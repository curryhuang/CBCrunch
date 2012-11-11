/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.crunch.impl.mr.exec;

import java.io.IOException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.crunch.hadoop.mapreduce.lib.jobcontrol.CrunchControlledJob;
import org.apache.crunch.impl.mr.plan.MSCROutputHandler;
import org.apache.crunch.impl.mr.plan.PlanningParameters;
import org.apache.crunch.io.FileNamingScheme;
import org.apache.crunch.io.PathTarget;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.FileUtil;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.util.StringUtils;

import com.google.common.collect.Lists;

public class CrunchJob extends CrunchControlledJob {

  private final Log log = LogFactory.getLog(CrunchJob.class);

  private final Path workingPath;
  private final Map<Integer, PathTarget> multiPaths;
  private final boolean mapOnlyJob;

  public CrunchJob(Job job, Path workingPath, MSCROutputHandler handler) throws IOException {
    super(job, Lists.<CrunchControlledJob> newArrayList());
    this.workingPath = workingPath;
    this.multiPaths = handler.getMultiPaths();
    this.mapOnlyJob = handler.isMapOnlyJob();
  }

  private synchronized void handleMultiPaths() throws IOException {
    if (!multiPaths.isEmpty()) {
      // Need to handle moving the data from the output directory of the
      // job to the output locations specified in the paths.
      FileSystem srcFs = workingPath.getFileSystem(job.getConfiguration());
      for (Map.Entry<Integer, PathTarget> entry : multiPaths.entrySet()) {
        final int i = entry.getKey();
        final Path dst = entry.getValue().getPath();
        FileNamingScheme fileNamingScheme = entry.getValue().getFileNamingScheme();

        Path src = new Path(workingPath, PlanningParameters.MULTI_OUTPUT_PREFIX + i + "-*");
        Path[] srcs = FileUtil.stat2Paths(srcFs.globStatus(src), src);
        Configuration conf = job.getConfiguration();
        FileSystem dstFs = dst.getFileSystem(conf);
        if (!dstFs.exists(dst)) {
          dstFs.mkdirs(dst);
        }
        boolean sameFs = isCompatible(srcFs, dst);
        for (Path s : srcs) {
          Path d = getDestFile(conf, s, dst, fileNamingScheme);
          if (sameFs) {
            srcFs.rename(s, d);
          } else {
            FileUtil.copy(srcFs, s, dstFs, d, true, true, job.getConfiguration());
          }
        }
      }
    }
  }

  private boolean isCompatible(FileSystem fs, Path path) {
    try {
      fs.makeQualified(path);
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  private Path getDestFile(Configuration conf, Path src, Path dir, FileNamingScheme fileNamingScheme)
      throws IOException {
    String outputFilename = null;
    if (mapOnlyJob) {
      outputFilename = fileNamingScheme.getMapOutputName(conf, dir);
    } else {
      outputFilename = fileNamingScheme.getReduceOutputName(conf, dir, CrunchJob.extractPartitionNumber(src.getName()));
    }
    if (src.getName().endsWith(org.apache.avro.mapred.AvroOutputFormat.EXT)) {
      outputFilename += org.apache.avro.mapred.AvroOutputFormat.EXT;
    }
    return new Path(dir, outputFilename);
  }

  @Override
  protected void checkRunningState() throws IOException, InterruptedException {
    try {
      if (job.isComplete()) {
        if (job.isSuccessful()) {
          handleMultiPaths();
          this.state = State.SUCCESS;
        } else {
          this.state = State.FAILED;
          this.message = "Job failed!";
        }
      }
    } catch (IOException ioe) {
      this.state = State.FAILED;
      this.message = StringUtils.stringifyException(ioe);
      try {
        if (job != null) {
          job.killJob();
        }
      } catch (IOException e) {
      }
    }
  }

  @Override
  protected synchronized void submit() {
    super.submit();
    if (this.state == State.RUNNING) {
      log.info("Running job \"" + getJobName() + "\"");
      log.info("Job status available at: " + job.getTrackingURL());
    } else {
      log.info("Error occurred starting job \"" + getJobName() + "\":");
      log.info(getMessage());
    }
  }

  /**
   * Extract the partition number from a raw reducer output filename.
   * 
   * @param fileName The raw reducer output file name
   * @return The partition number encoded in the filename
   */
  static int extractPartitionNumber(String reduceOutputFileName) {
    Matcher matcher = Pattern.compile(".*-r-(\\d{5})").matcher(reduceOutputFileName);
    if (matcher.find()) {
      return Integer.parseInt(matcher.group(1), 10);
    } else {
      throw new IllegalArgumentException("Reducer output name '" + reduceOutputFileName + "' cannot be parsed");
    }
  }
}
