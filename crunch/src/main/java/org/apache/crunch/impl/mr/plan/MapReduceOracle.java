package org.apache.crunch.impl.mr.plan;

import java.util.Map;

import org.apache.hadoop.conf.Configuration;

import com.google.common.collect.Maps;

public class MapReduceOracle {
	
	public static final long MB = 1024 * 1024;
	
	public static final String MAP_SORT_MB = "io.sort.mb";
	public static final String MAP_SPILL_PERC = "io.sort.spill.percent";
	public static final String MAP_SORT_REC_PERC = "io.sort.record.percent";
	public static final String MAP_SORT_FACTOR = "io.sort.factor";
	public static final String MAP_NUM_SPILL_FOR_COMB = "min.num.spills.for.combine";
	public static final String DFS_BLOCK_SIZE = "dfs.block.size";
	public static final String MAP_SIZE_SEL = "sel.size.map";
	public static final String MAP_RECS_SEL = "sel.recs.map";
	public static final String REDUCE_SIZE_SEL = "sel.size.reduce";
	public static final String REDUCE_RECS_SEL = "sel.recs.reduce";
	public static final String COMBINE_SIZE_SEL = "sel.size.combine";
	public static final String COMBINE_RECS_SEL = "sel.recs.combine";
	
	private Configuration conf;
	private Map<String, String> oracle;
	
	public MapReduceOracle(Configuration conf){
		this.conf = conf;
		oracle = Maps.newHashMap();
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
	
	public float getMapSizeSel(){
		return oracle.containsKey(MAP_SIZE_SEL) ? Float.parseFloat(oracle.get(MAP_SIZE_SEL)) : 1.0f;
	}
	
	public float getMapRecsSel(){
		return oracle.containsKey(MAP_RECS_SEL) ? Float.parseFloat(oracle.get(MAP_RECS_SEL)) : 1.0f;
	}
	
	public float getReduceSizeSel(){
		return oracle.containsKey(REDUCE_SIZE_SEL) ? Float.parseFloat(oracle.get(REDUCE_SIZE_SEL)) : 1.0f;
	}
	
	public float getReduceRecsSel(){
		return oracle.containsKey(REDUCE_RECS_SEL) ? Float.parseFloat(oracle.get(REDUCE_RECS_SEL)) : 1.0f;
	}
	
	public float getCombineSizeSel(){
		return oracle.containsKey(COMBINE_SIZE_SEL) ? Float.parseFloat(oracle.get(COMBINE_SIZE_SEL)) : 1.0f;
	}
	
	public float getCombineRecsSel(){
		return oracle.containsKey(COMBINE_RECS_SEL) ? Float.parseFloat(oracle.get(COMBINE_RECS_SEL)) : 1.0f;
	}
	
	public MapReduceOracle set(String paraname, String value){
		oracle.put(paraname, value);
		return this;
	}
}
