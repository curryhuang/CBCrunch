package org.apache.crunch.impl.mr.plan;

import org.apache.hadoop.conf.Configuration;

public class MapReduceOracle {
	
	public static final long MB = 1024 * 1024;
	
	public static final String MAP_SORT_MB = "io.sort.mb";
	public static final String MAP_SPILL_PERC = "io.sort.spill.percent";
	public static final String MAP_SORT_REC_PERC = "io.sort.record.percent";
	public static final String MAP_SORT_FACTOR = "io.sort.factor";
	public static final String MAP_NUM_SPILL_FOR_COMB = "min.num.spills.for.combine";
	public static final String DFS_BLOCK_SIZE = "dfs.block.size";
	
	private Configuration conf;
	
	public MapReduceOracle(Configuration conf){
		this.conf = conf;
	}
	
	public Configuration getConf() {
		return conf;
	}

	public long getpSortMB() {
		return conf.getLong(MAP_SORT_MB, 100);
	}

	public float getpSpillPerc() {
		return conf.getFloat(MAP_SPILL_PERC, 0.8f);
	}

	public float getpSortRecPerc() {
		return conf.getFloat(MAP_SORT_REC_PERC, 0.05f);
	}

	public int getpSortFactor() {
		return conf.getInt(MAP_SORT_FACTOR, 10);
	}

	public int getpNumSpillForComb() {
		return conf.getInt(MAP_SORT_MB, 3);
	}
	
	public long getDFSBlockSizeMB(){
		return conf.getLong(DFS_BLOCK_SIZE, 64);
	}
}
