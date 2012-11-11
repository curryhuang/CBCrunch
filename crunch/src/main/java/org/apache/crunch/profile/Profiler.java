package org.apache.crunch.profile;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.crunch.DoFn;
import org.apache.crunch.PCollection;
import org.apache.crunch.PTable;
import org.apache.crunch.Pair;
import org.apache.crunch.Pipeline;
import org.apache.crunch.PipelineResult;
import org.apache.crunch.PipelineResult.StageResult;
import org.apache.crunch.io.SourceTargetHelper;
import org.apache.crunch.types.PTableType;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapred.Task;
import org.apache.hadoop.mapreduce.Counter;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;

@SuppressWarnings("serial")
public class Profiler implements Serializable{
	
	private final Log log = LogFactory.getLog(Profiler.class);
	
	public static final String PROFILE_DIR = "crunch_profile";
	public static final String IS_PROFILE = "crunch.profiler.profiling";
	
	
	private boolean isProfiling;
	private Element scaleFactors;
	
	private Path createProfileDirectory(Configuration conf) {
	    Path dir = new Path(PROFILE_DIR);
	    try {
	      dir.getFileSystem(conf).mkdirs(dir);
	    } catch (IOException e) {
	      throw new RuntimeException("Cannot create job output directory " + dir, e);
	    }
	    return dir;
	  }

	public Profiler(Pipeline p){
		scaleFactors = new Element("scalefactors");
		this.isProfiling = p.getConfiguration().getBoolean(IS_PROFILE, false);
	}
	
	public <K, V> PTable<K, V> profile(String stageName, Pipeline pipeline, PTable<K, V> table, DoFn<String, Pair<K, V>> fn, PTableType<K, V> outputType){
		if(!isProfiling){
			return table;
		}
		else{
			String name = createProfileDirectory(pipeline.getConfiguration()).getName();
			String pathName = name + "/" + table.getName();
			pipeline.writeTextFile(table, pathName);
			PipelineResult result = pipeline.done();
			handleResult(stageName, pathName, result, pipeline.getConfiguration());
			PCollection<String> next = pipeline.readTextFile(pathName);
			
			return next.parallelDo(fn, outputType);
		}
	}
	
	public boolean isProfiling(){
		return isProfiling;
	}
	
	public Document getProfileResult(){
		Element root = new Element("estimation").addContent(scaleFactors);
		return new Document(root);
	}
	
	public void writeResultToFile(String filename){
		XMLOutputter XMLOut = new XMLOutputter(Format.getPrettyFormat());  
	    try {
			XMLOut.output(getProfileResult(), new FileOutputStream(filename));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}  
	}
	
	public void cleanup(Configuration conf) {
		try {
			Path path = new Path(PROFILE_DIR);
			FileSystem fs = path.getFileSystem(conf);
			if (fs.exists(path)) {
				fs.delete(path, true);
			}
		} catch (IOException e) {
			log.info("Exception during cleanup", e);
		}
	}
	
	private void handleResult(String stageName, String outputPath, PipelineResult result, Configuration conf){
		String map;
		String reduce = getReduceName(stageName);
		StageResult sr = result.getStageResults().get(0);
		if(!mapOnly(stageName)){
			Pair<Float, Float> mapSel = getMapSel(sr, conf, outputPath);
			System.out.println(stageName + "\t" + mapSel.first() + "\t" + mapSel.second());
			createXMLNode(stageName, mapSel);
		}
		else if(reduce != null){
			Pair<Float, Float> reduceSel = getReduceSel(sr, conf, outputPath);
			System.out.println(reduce + "\t" + reduceSel.first() + "\t" + reduceSel.second());
			createXMLNode(reduce, reduceSel);
		}
		else{
			Pair<String, String> mapred = getMapReduceName(stageName);
			map = mapred.first();
			reduce = mapred.second();
			Pair<Float, Float> mapSel = getMapSel(sr);
			Pair<Float, Float> reduceSel = getReduceSel(sr, conf, outputPath);
			System.out.println(map + "\t" + mapSel.first() + "\t" + mapSel.second());
			System.out.println(reduce + "\t" + reduceSel.first() + "\t" + reduceSel.second());
			createXMLNode(map, mapSel);
			createXMLNode(reduce, reduceSel);
		}
	}
	
	private Pair<Float, Float> getMapSel(StageResult sr){
		
		long numMapInRecords = sr.findCounter(Task.Counter.MAP_INPUT_RECORDS).getValue();
		long numMapInBytes = sr.findCounter(FileInputFormat.Counter.BYTES_READ).getValue();
		long numMapOutBytes = sr.findCounter(Task.Counter.MAP_OUTPUT_BYTES).getValue();
		long numMapOutRecords = sr.findCounter(Task.Counter.MAP_OUTPUT_RECORDS).getValue();
		
		float mapBytesSel = numMapOutBytes * 1.0f / numMapInBytes;
		float mapRecsSel = numMapOutRecords * 1.0f / numMapInRecords;
		
		return Pair.of(mapBytesSel, mapRecsSel);
	}
	
	private Pair<Float, Float> getMapSel(StageResult sr, Configuration conf, String outputPath){
		long numMapInRecords = sr.findCounter(Task.Counter.MAP_INPUT_RECORDS).getValue();
		long numMapInBytes = sr.findCounter(FileInputFormat.Counter.BYTES_READ).getValue();
		long numMapOutRecords = 0;
		long numMapOutBytes = 0;
		try {
			numMapOutRecords = SourceTargetHelper.getPathTextFileSizeByRecord(conf, new Path(outputPath));
			numMapOutBytes = SourceTargetHelper.getPathSize(conf, new Path(outputPath));
		} catch (IOException e) {
			log.info("Fail to profiling, " + outputPath + " does not exist");
			e.printStackTrace();
		}
		
		float mapBytesSel = numMapOutBytes * 1.0f / numMapInBytes;
		float mapRecsSel = numMapOutRecords * 1.0f / numMapInRecords;
		
		return Pair.of(mapBytesSel, mapRecsSel);
	}
	
	private Pair<Float, Float> getReduceSel(StageResult sr, Configuration conf, String outputPath){
		long numReduceInRecords = sr.findCounter(Task.Counter.MAP_OUTPUT_RECORDS).getValue();
		long numReduceInBytes = sr.findCounter(Task.Counter.MAP_OUTPUT_BYTES).getValue();
		long numReduceOutRecords = 0;
		long numReduceOutBytes = 0;
		try {
			numReduceOutRecords = SourceTargetHelper.getPathTextFileSizeByRecord(conf, new Path(outputPath));
			numReduceOutBytes = SourceTargetHelper.getPathSize(conf, new Path(outputPath));
		} catch (IOException e) {
			log.info("Fail to profiling, " + outputPath + " does not exist");
			e.printStackTrace();
		}
		
		float reduceBytesSel = numReduceOutBytes * 1.0f / numReduceInBytes;
		float reduceRecsSel = numReduceOutRecords * 1.0f / numReduceInRecords;
		
		return Pair.of(reduceBytesSel, reduceRecsSel);
	}
	
	private boolean mapOnly(String stageName){
		return stageName.contains("-");
	}
	
	private String getReduceName(String stageName){
		if(stageName.contains("-") && stageName.contains("+")){
			return stageName.split("-")[1];
		}
		return null;
	}
	
	private Pair<String, String> getMapReduceName(String stageName){
		String[] mapred = stageName.split("-");
		return Pair.of(mapred[0], mapred[1]);
	}
	
	private void createXMLNode(String name, Pair<Float, Float> selectivity){
		Element e = new Element(name);
		e.addContent(new Element("size").setText(String.valueOf(selectivity.first())));
		e.addContent(new Element("recs").setText(String.valueOf(selectivity.second())));
		scaleFactors.addContent(e);
	}
}
