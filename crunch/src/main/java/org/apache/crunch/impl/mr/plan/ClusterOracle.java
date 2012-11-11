package org.apache.crunch.impl.mr.plan;

import org.apache.hadoop.conf.Configuration;

public class ClusterOracle {
	public static final String CLUSTER_SIZE = "cluster.node.size";
	
	private Configuration conf;
	
	public ClusterOracle(Configuration conf) {
		this.conf = conf;
	}
	
	public int getClusterSize(){
		return conf.getInt(CLUSTER_SIZE, 1);
	}
}
