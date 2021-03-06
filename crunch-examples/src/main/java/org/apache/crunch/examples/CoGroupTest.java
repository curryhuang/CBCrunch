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
import java.util.Collection;

import org.apache.crunch.DoFn;
import org.apache.crunch.Emitter;
import org.apache.crunch.FilterFn;
import org.apache.crunch.MapFn;
import org.apache.crunch.PCollection;
import org.apache.crunch.PTable;
import org.apache.crunch.Pair;
import org.apache.crunch.Pipeline;
import org.apache.crunch.PipelineResult;
import org.apache.crunch.Target;
import org.apache.crunch.impl.mr.MRPipeline;
import org.apache.crunch.lib.Cogroup;
import org.apache.crunch.types.writable.Writables;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.util.GenericOptionsParser;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

@SuppressWarnings("serial")
public class CoGroupTest extends Configured implements Tool, Serializable {
	public int run(String[] args) throws Exception {
		if (args.length != 3) {
			System.err.println();
			System.err.println("Usage: " + this.getClass().getName()
					+ " [generic options] input output");
			System.err.println();
			GenericOptionsParser.printGenericCommandUsage(System.err);
			return 1;
		}
		// Create an object to coordinate pipeline creation and execution.
		Pipeline pipeline = new MRPipeline(CoGroupTest.class, getConf());
		// Reference a given text file as a collection of Strings.
		PCollection<String> lines1 = pipeline.readTextFile(args[0]);
		PCollection<String> lines2 = pipeline.readTextFile(args[1]);

		PTable<String, String> group1 = lines1.parallelDo(new PairMapFn(), Writables.tableOf(Writables.strings(), Writables.strings()));
		PTable<String, String> group2 = lines2.parallelDo(new PairMapFn(), Writables.tableOf(Writables.strings(), Writables.strings()));
		
		PTable<String, Pair<Collection<String>, Collection<String>>> cogroup = Cogroup.cogroup(group1, group2);
		
		PCollection<String> line3 = pipeline.readTextFile("src/main/resources/CoGroup/input3");
		pipeline.writeTextFile(cogroup, args[2]);
		// Execute the pipeline as a MapReduce.
		PipelineResult result = pipeline.done();

		return result.succeeded() ? 0 : 1;
	}
	
	public static class PairMapFn extends MapFn<String, Pair<String, String>>{

		@Override
		public Pair<String, String> map(String input) {
			String[] split = input.split("\\s+");
			return Pair.of(split[0], split[1]);
		}
		
	}

	public static void main(String[] args) throws Exception {
		int result = ToolRunner.run(new Configuration(), new CoGroupTest(),
				args);
		System.exit(result);
	}
}
