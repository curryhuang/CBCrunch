package org.apache.crunch.impl.mr.plan;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.crunch.CombineFn;
import org.apache.crunch.PCollection;
import org.apache.crunch.Pipeline;
import org.apache.crunch.impl.mr.collect.DoTableImpl;
import org.apache.crunch.impl.mr.collect.PCollectionImpl;
import org.apache.crunch.impl.mr.collect.PGroupedTableImpl;
import org.apache.hadoop.conf.Configuration;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class MSCROptimizer {

	private final Log log = LogFactory.getLog(MSCROptimizer.class);
	private Set<NodePath> paths;
	private MapReduceOracle MROracle;
	private ClusterOracle clusterOracle;
	private boolean opt = false;

	public MSCROptimizer(Set<NodePath> paths) {
		this.paths = paths;
	}

	public int getSplitIndex() {
		Map<Integer, Long> costMap = Maps.newHashMap();
		List<Iterator<PCollectionImpl<?>>> iters = Lists.newArrayList();
		for (NodePath nodePath : paths) {
			Iterator<PCollectionImpl<?>> iter = nodePath.iterator();
			PCollectionImpl<?> groupedTable = iter.next(); // prime this past
															// the initial
															// NGroupedTableImpl
			opt = groupedTable.getPipeline().getConfiguration().getBoolean("opt.mscr", false);
			if (MROracle == null) {
				MROracle = getMapReduceOracle(groupedTable.getPipeline());
			}
			if(clusterOracle == null){
				clusterOracle = getClusterOracle(groupedTable.getPipeline());
			}
			iters.add(iter);
		}
		
		// Find the lowest point w/the lowest cost to be the split point for
		// all of the dependent paths.
		List<PCollectionImpl<?>> list = Lists.newArrayList();
		boolean end = false;
		int splitIndex = -1;
		while (!end) {
			splitIndex++;
			PCollectionImpl<?> current = null;
			for (Iterator<PCollectionImpl<?>> iter : iters) {
				if (iter.hasNext()) {
					PCollectionImpl<?> next = iter.next();
					if (next instanceof PGroupedTableImpl) {
						end = true;
						break;
					} else if (current == null) {
						current = next;
						list.add(current);
					} else if (current != next) {
						end = true;
						break;
					}
				} else {
					end = true;
					break;
				}
			}
		}
		if (!opt) {
			return splitIndex;
		} else {
			// Costing calcs here.
			splitIndex = 0;
			for (; splitIndex < list.size(); splitIndex++) {
				// TODO do calculating when node has only one parent now,
				// should handle multiple parents case in the future.
				PCollectionImpl<?> lastReduceNode = list.get(splitIndex);
				float mapSizeSel = 1;
				float mapRecsSel = 1;
				int lastIndex = list.size() - 1;
				if (splitIndex != lastIndex) {
					mapSizeSel = list.get(lastIndex).getSize() * 1.0f / lastReduceNode.getSize();
					mapRecsSel = list.get(lastIndex).getSizeByRecord() * 1.0f / lastReduceNode.getSizeByRecord();
					MROracle.set(MapReduceOracle.MAP_SIZE_SEL, String.valueOf(mapSizeSel));
					MROracle.set(MapReduceOracle.MAP_RECS_SEL, String.valueOf(mapRecsSel));
				}
				log.info("[" + paths.toString() + " " + (splitIndex + 1) + " sel: " + mapSizeSel + "|" + mapRecsSel
						+ "]");
				long mapCost = getNextMapCost(lastReduceNode);
				long reduceCost = getPreReduceCost(lastReduceNode);
				log.info("[" + paths.toString() + " " + (splitIndex + 1) + " cost: " + (mapCost + reduceCost) + "]");
				costMap.put(splitIndex + 1, mapCost + reduceCost);
			}
			int ret = findLowestCostIndex(costMap);
			log.info(paths.toString() + " Split at " + ret);
			return ret;
		}
	}

	private ClusterOracle getClusterOracle(Pipeline pipeline) {
		return new ClusterOracle(pipeline.getConfiguration());
	}

	protected int findLowestCostIndex(Map<Integer, Long> map) {
		int idx = 1;
		long cost = map.get(idx);
		for (Map.Entry<Integer, Long> entry : map.entrySet()) {
			int i = entry.getKey();
			long c = entry.getValue();
			if (c < cost) {
				idx = i;
				cost = c;
			}
		}
		return idx;
	}

	protected MapReduceOracle getMapReduceOracle(Pipeline pipeline) {
		Configuration conf = pipeline.getConfiguration();
		return new MapReduceOracle(conf);
	}

	protected long getNextMapCost(PCollectionImpl<?> inputCollect) {
		//read cost
		long mapReadCost = inputCollect.getSize();
		//inner cost
		long splitSize = inputCollect.getSize();
		long numSplit = 1 + splitSize / (MROracle.getDFSBlockSizeMB() * MapReduceOracle.MB);
		long inputPairWidth = (long) (splitSize / inputCollect.getSizeByRecord());
		if (splitSize > MROracle.getDFSBlockSizeMB() * MapReduceOracle.MB) {
			splitSize = MROracle.getDFSBlockSizeMB() * MapReduceOracle.MB;
		}
		CostCalculator cc = new MapCostCalculator(splitSize, inputPairWidth, MROracle);
		return numSplit * cc.getCost() + mapReadCost;
	}
	
	protected long getPreReduceCost(PCollectionImpl<?> reduceNode){
		return reduceNode.getSize();
	}
}
