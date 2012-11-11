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

import org.apache.crunch.DoFn;
import org.apache.crunch.Emitter;
import org.apache.crunch.MapFn;
import org.apache.crunch.PCollection;
import org.apache.crunch.PGroupedTable;
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
public class ReversedWordCount extends Configured implements Tool, Serializable {
@SuppressWarnings("unchecked")
public int run(String[] args) throws Exception {
    if (args.length != 3) {
      System.err.println();
      System.err.println("Usage: " + this.getClass().getName() + " [generic options] input output");
      System.err.println();
      GenericOptionsParser.printGenericCommandUsage(System.err);
      return 1;
    }
    // Create an object to coordinate pipeline creation and execution.
    Pipeline pipeline = new MRPipeline(ReversedWordCount.class, getConf());
    // Reference a given text file as a collection of Strings.
    PCollection<String> lines1 = pipeline.readTextFile(args[0]);
    PCollection<String> lines2 = pipeline.readTextFile(args[1]);
    
    PCollection<String> lines = lines1.union(lines2);

    // Define a function that splits each line in a PCollection of Strings into
    // a
    // PCollection made up of the individual words in the file.
    PCollection<String> words = lines.parallelDo(new DoFn<String, String>() {
      public void process(String line, Emitter<String> emitter) {
        for (String word : line.split("\\s+")) {
          emitter.emit(word);
        }
      }
    }, Writables.strings()); // Indicates the serialization format

    // The count method applies a series of Crunch primitives and returns
    // a map of the unique words in the input PCollection to their counts.
    // Best of all, the count() function doesn't need to know anything about
    // the kind of data stored in the input PCollection.
    PTable<String, Long> counts = words.count();
    
    PTable<Long, String> reversedCount = counts.parallelDo(new DoFn<Pair<String, Long>, Pair<Long, String>>(){

		@Override
		public void process(Pair<String, Long> input,
				Emitter<Pair<Long, String>> emitter) {
			emitter.emit(Pair.of(input.second(), input.first()));
		}
		
    }, Writables.tableOf(Writables.longs(), Writables.strings()));
    
    PTable<Long, String> reversed = reversedCount.groupByKey().combineValues(new CombineByWordCount());

    // Instruct the pipeline to write the resulting counts to a text file.
    pipeline.writeTextFile(reversed, args[2]);
    // Execute the pipeline as a MapReduce.
    PipelineResult result = pipeline.done();

    return result.succeeded() ? 0 : 1;
  }

  public static void main(String[] args) throws Exception {
    int result = ToolRunner.run(new Configuration(), new ReversedWordCount(), args);
    System.exit(result);
  }
}
