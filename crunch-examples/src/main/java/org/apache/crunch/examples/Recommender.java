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
import java.util.Iterator;

import org.apache.crunch.DoFn;
import org.apache.crunch.Emitter;
import org.apache.crunch.MapFn;
import org.apache.crunch.PCollection;
import org.apache.crunch.PGroupedTable;
import org.apache.crunch.PTable;
import org.apache.crunch.Pair;
import org.apache.crunch.Pipeline;
import org.apache.crunch.PipelineResult;
import org.apache.crunch.impl.mr.MRPipeline;
import org.apache.crunch.types.writable.Writables;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.util.GenericOptionsParser;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.apache.mahout.math.RandomAccessSparseVector;
import org.apache.mahout.math.Vector;


@SuppressWarnings("serial")
public class Recommender extends Configured implements Tool, Serializable {
  public int run(String[] args) throws Exception {
    if (args.length != 2) {
      System.err.println();
      System.err.println("Usage: " + this.getClass().getName() + " [generic options] input output");
      System.err.println();
      GenericOptionsParser.printGenericCommandUsage(System.err);
      return 1;
    }
    Pipeline pipeline = new MRPipeline(Recommender.class, getConf());
    /*
     * input node
     */
    PCollection<String> lines = pipeline.readTextFile(args[0]);
    
    /*
     * S0 + GBK
     */
    PGroupedTable<Long, Long> userWithPrefs = lines.parallelDo(new MapFn<String, Pair<Long, Long>>() {

		@Override
		public Pair<Long, Long> map(String input) {
			String[] split = input.split("[,\\s]");
			long userID = Long.parseLong(split[0]);
			long itemID = Long.parseLong(split[1]);
			return Pair.of(userID, itemID);
		}
    }, Writables.tableOf(Writables.longs(), Writables.longs())).groupByKey(); 
    
    /*
     * S1
     */
    PTable<Long, Vector> userVector = userWithPrefs.parallelDo(new MapFn<Pair<Long, Iterable<Long>>, Pair<Long, Vector>>(){
		@Override
		public Pair<Long, Vector> map(Pair<Long, Iterable<Long>> input) {
			Vector userVector = new RandomAccessSparseVector(Integer.MAX_VALUE, 100);
			for (long itemPref : input.second()) {
				userVector.set((int)itemPref, 1.0f);
			}
			return Pair.of(input.first(), userVector);
		}
    }, Writables.tableOf(Writables.longs(), Writables.vectors()));
    
    /*
     * S2 + GBK
     */
    PGroupedTable<Integer, Integer> coOccurencePairs = userVector.parallelDo(new DoFn<Pair<Long, Vector>, Pair<Integer, Integer>>(){
		@Override
		public void process(Pair<Long, Vector> input, Emitter<Pair<Integer, Integer>> emitter) {
			Iterator<Vector.Element> it = input.second().iterateNonZero();
			while (it.hasNext()) {
				int index1 = it.next().index();
				Iterator<Vector.Element> it2 = input.second().iterateNonZero();
				while (it2.hasNext()) {
					int index2 = it2.next().index();
					emitter.emit(Pair.of(index1, index2));
				}
			}
		}
	}, Writables.tableOf(Writables.ints(), Writables.ints())).groupByKey();
    
    /*
     * S3
     */
    PTable<Integer, Vector> coOccurenceVector = coOccurencePairs.parallelDo(new MapFn<Pair<Integer, Iterable<Integer>>, Pair<Integer, Vector>>(){
		@Override
		public Pair<Integer, Vector> map(Pair<Integer, Iterable<Integer>> input) {
			Vector cooccurrenceRow = new RandomAccessSparseVector(Integer.MAX_VALUE, 100);
			for (int itemIndex2 : input.second()) {
				cooccurrenceRow.set(itemIndex2, cooccurrenceRow.get(itemIndex2) + 1.0);
			}
			return Pair.of(input.first(), cooccurrenceRow);
		}
    }, Writables.tableOf(Writables.ints(), Writables.vectors()));
    
    /*
     * asText
     */
    pipeline.writeTextFile(coOccurenceVector, args[1]);
    PipelineResult result = pipeline.done();

    return result.succeeded() ? 0 : 1;
  }

  public static void main(String[] args) throws Exception {
    int result = ToolRunner.run(new Configuration(), new Recommender(), args);
    System.exit(result);
  }
}
