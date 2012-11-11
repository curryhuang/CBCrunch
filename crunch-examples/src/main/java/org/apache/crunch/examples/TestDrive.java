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
package org.apache.crunch.examples;

import java.io.Serializable;

import org.apache.crunch.MapFn;
import org.apache.crunch.PCollection;
import org.apache.crunch.PTable;
import org.apache.crunch.Pair;
import org.apache.crunch.Pipeline;
import org.apache.crunch.PipelineResult;
import org.apache.crunch.examples.fn.CombineByWordCount;
import org.apache.crunch.impl.mr.MRPipeline;
import org.apache.crunch.types.writable.Writables;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.util.GenericOptionsParser;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

@SuppressWarnings("serial")
public class TestDrive extends Configured implements Tool, Serializable {
public int run(String[] args) throws Exception {
    if (args.length != 2) {
      System.err.println();
      System.err.println("Usage: " + this.getClass().getName() + " [generic options] input output");
      System.err.println();
      GenericOptionsParser.printGenericCommandUsage(System.err);
      return 1;
    }
    
    Pipeline pipeline = new MRPipeline(TestDrive.class, getConf());
    PCollection<String> lines = pipeline.readTextFile(args[0]);
    
    PTable<Long, String> countWithWords = lines.parallelDo(new SplitWordCountFn(),
    		Writables.tableOf(Writables.longs(), Writables.strings())); // Indicates the serialization format
    
    PTable<Long, String> groupedWordByCount = countWithWords.groupByKey().combineValues(new CombineByWordCount());

    pipeline.writeTextFile(groupedWordByCount, args[1]);
    PipelineResult result = pipeline.done();
    
    return result.succeeded() ? 0 : 1;
  }

  public static void main(String[] args) throws Exception {
    int result = ToolRunner.run(new Configuration(), new TestDrive(), args);
    System.exit(result);
  }
}

@SuppressWarnings("serial")
class SplitWordCountFn extends MapFn<String, Pair<Long, String>>{

	@Override
	public Pair<Long, String> map(String input) {
		String[] split = input.split("\\s+");
		return Pair.of(Long.parseLong(split[0]), split[1]);
	}
}
