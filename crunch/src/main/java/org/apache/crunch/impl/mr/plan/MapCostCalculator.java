package org.apache.crunch.impl.mr.plan;

public class MapCostCalculator implements CostCalculator {
	
	private long splitSize;
	private long inputPairWidth;
	private float mapSizeSel;
	private float mapRecsSel;
	private long numSpills;
	private long spillFileRecs;
	private long spillFileSize;
	private MapReduceOracle oracle;
	
	public MapCostCalculator(long splitSize,
							 long inputPairWidth,
							 float mapSizeSel,
							 float mapRecsSel, 
							 MapReduceOracle oracle){
		this.splitSize = splitSize;
		this.inputPairWidth = inputPairWidth;
		this.mapSizeSel = mapSizeSel;
		this.mapRecsSel = mapRecsSel;
		this.oracle = oracle;
	}
	
	@Override
	public long getCost() {
		long cost = calcCollectAndSpillCost() + calcMergeCost();
		return cost;
	}
	
	private long calcCollectAndSpillCost(){
		long mapInBytes = splitSize;
		long mapInRecs = mapInBytes / inputPairWidth;
		long mapOutBytes = (long) (mapInBytes * mapSizeSel);
		long mapOutRecs = (long) (mapInRecs * mapRecsSel);
		long mapOutRecWidth = mapOutBytes / mapOutRecs;
		
		long MB = MapReduceOracle.MB;
		long pSortMB = oracle.getpSortMB();
		float pSortRecPerc = oracle.getpSortRecPerc();
		float pSpillPerc = oracle.getpSpillPerc();
		long maxSerRecs = (long) ((oracle.getpSortMB() * 2 * MB * (1 - oracle.getpSortRecPerc())) / mapOutRecWidth);
		long maxAccRecs = (long) ((pSortMB * MB * pSortRecPerc * pSpillPerc) / 16);
		
		long spillBufferRecs = Math.min(Math.min(maxSerRecs, maxAccRecs), mapOutRecs);
		long spillBufferSize = spillBufferRecs * mapOutRecWidth;
		long numSpills = (long) Math.ceil(mapOutRecs / spillBufferRecs);
		this.numSpills = numSpills;
		this.spillFileSize = spillBufferSize;
		this.spillFileRecs = spillBufferRecs;
		
		return numSpills * spillBufferRecs;
	}
	
	private long calcMergeCost(){
		long pSortFactor = oracle.getpSortFactor();
		
		if(numSpills == 1){
			return 0;
		}
		else if(numSpills <= pSortFactor){
			//read all the records in spills and write them to one file
			return 2 * spillFileRecs * numSpills;
		}
		else {
			long numSpillsFirstPass = calcNumSpillsFirstPass(numSpills, pSortFactor);
			long numSpillsIntermMerge = calcNumSpillsIntermMerge(numSpills, pSortFactor);
			long numSpillsFinalMerge = calcNumSpillsFinalMerge(numSpills, pSortFactor);
			long numMergePasses = (long) (2 + Math.floor((numSpills - numSpillsFirstPass) / pSortFactor));
			
			return 2 * spillFileRecs * (numSpillsFirstPass + numSpillsIntermMerge + numSpillsFinalMerge);
		}
	}
	
	private long calcNumSpillsFirstPass(long N, long F){
		if(N <= F){
			return N;
		}
		else if((N - 1) % (F - 1) == 0){
			return F;
		}
		else{
			return (N - 1) % (F - 1) + 1;
		}
	}
	
	private long calcNumSpillsIntermMerge(long N, long F){
		if(N <= F){
			return N;
		}
		else{
			long P = calcNumSpillsFirstPass(N, F);
			return (long) (P + Math.floor((N - P) / F) * F);
		}
	}
	
	private long calcNumSpillsFinalMerge(long N, long F){
		long P = calcNumSpillsFirstPass(N, F);
		long S = calcNumSpillsIntermMerge(N, F);
		if(N <= F){
			return N;
		}
		else{
			return (long) (1 + Math.floor((N - P) / F) + (N - S));
		}
	}

}
