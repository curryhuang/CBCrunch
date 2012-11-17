package org.apache.crunch.impl.mr.plan;

public class MapCostCalculator implements CostCalculator {
	
	private long MB = MapReduceOracle.MB;
	
	private long splitSize;
	private long inputPairWidth;
	private float mapSizeSel;
	private float mapRecsSel;
	private float combineSizeSel;
	private long numSpills;
	private long spillFileSize;
	private MapReduceOracle oracle;
	
	public MapCostCalculator(long splitSize,
							 long inputPairWidth,
							 MapReduceOracle oracle){
		this.splitSize = splitSize;
		this.inputPairWidth = inputPairWidth;
		this.mapSizeSel = oracle.getMapSizeSel();
		this.mapRecsSel = oracle.getMapRecsSel();
		this.combineSizeSel = oracle.getCombineSizeSel();
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
		
		long pSortMB = oracle.getpSortMB();
		float pSortRecPerc = oracle.getpSortRecPerc();
		float pSpillPerc = oracle.getpSpillPerc();
		long maxSerRecs = (long) ((oracle.getpSortMB() * 2 * MB * (1 - oracle.getpSortRecPerc())) / mapOutRecWidth);
		long maxAccRecs = (long) ((pSortMB * MB * pSortRecPerc * pSpillPerc) / 16);
		
		long spillBufferRecs = Math.min(Math.min(maxSerRecs, maxAccRecs), mapOutRecs);
		long spillBufferSize = spillBufferRecs * mapOutRecWidth;
		long numSpills = (long) Math.ceil(mapOutRecs / spillBufferRecs);
		this.numSpills = numSpills;
		this.spillFileSize = (long) (spillBufferSize * combineSizeSel);
		
		return numSpills * spillFileSize;
	}
	
	private long calcMergeCost(){
		long pSortFactor = oracle.getpSortFactor();
		
		if(numSpills == 1){
			return 0;
		}
		else if(numSpills <= pSortFactor){
			//read all the records in spills and write them to one file
			return 2 * spillFileSize * numSpills;
		}
		else {
			long numSpillsFirstPass = calcNumSpillsFirstPass(numSpills, pSortFactor);
			long numMergePasses = (long) (2 + Math.floor((numSpills - numSpillsFirstPass) / pSortFactor));
			
			long firstCost = numSpillsFirstPass * spillFileSize * 2;
			long intermCost = (numMergePasses - 2) * spillFileSize * 2;
			long finalCost = 2 * splitSize;
			return firstCost + intermCost + finalCost;
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
}
