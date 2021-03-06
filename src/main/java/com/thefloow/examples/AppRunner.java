package com.thefloow.examples;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.mapreduce.MapReduceOptions;
import org.springframework.data.mongodb.core.mapreduce.MapReduceResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import com.google.gson.Gson;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.thefloow.examples.beans.CollectedWord;
import com.thefloow.examples.beans.Offset;
import com.thefloow.examples.beans.ParsedData;
import com.thefloow.examples.beans.PopularWord;

@SpringBootApplication
public class AppRunner implements CommandLineRunner {

	private static Logger log = Logger.getLogger(AppRunner.class.getCanonicalName());

	@Autowired
	private PopularWordService popularWordService;
	@Autowired
	private MongoOperations operations;
	@Autowired
	private Gson gson;

	public static void main(String[] args) {
		SpringApplication.run(AppRunner.class, args);
	}

	@PostConstruct
	private void initialize() {
		DBCollection popularWord = operations.getCollection(operations.getCollectionName(CollectedWord.class));
		DBCollection collectedWord = operations.getCollection(operations.getCollectionName(PopularWord.class));
		if((popularWord.count( ) == 0) && (collectedWord.count( ) == 0)) {
			operations.executeCommand(new BasicDBObject("eval", "classpath:com/thefloow/examples/configs/thefloow.schema.js"));
		}
	}

	@Override
	public void run(String... args) throws Exception {
		if(args.length > 0) {
			if("elaborate".equals(args[0])) {
				log.log(Level.INFO, "Started Processor in Elaborate Mode");
				operations.getCollection(operations.getCollectionName(PopularWord.class)).remove(new BasicDBObject());
				MapReduceOptions mapReduceOptions = MapReduceOptions.options();
				mapReduceOptions.outputTypeReduce();
				mapReduceOptions.outputCollection(operations.getCollectionName(PopularWord.class));
				MapReduceResults<CollectedWord> result = operations.mapReduce(operations.getCollectionName(CollectedWord.class), "classpath:com/thefloow/examples/configs/mapFunc.js", "classpath:/com/thefloow/examples/configs/reduceFunc.js", mapReduceOptions, CollectedWord.class);
				for(CollectedWord entry : result) {
					System.out.println(entry);
				}
				result.getRawResults();
			}else if("write".equals(args[0])) {
				log.log(Level.INFO, "Started Processor in Write Mode");
				InputStream isData = null;
				try {
					isData = AppRunner.class.getResourceAsStream("/com/thefloow/examples/configs/data.json");
					BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(isData));
					String line = null;
					double offset = 0;
					double index = 0;
					Offset offsetBean = null;
					Query query = new Query();
					query.addCriteria(Criteria.where("filename").is("data.json"));
					Update update = new Update();
					update.inc("value", 1);
					offsetBean = operations.findOne(query, Offset.class);
					offset = offsetBean.getValue().doubleValue();
					while((line = bufferedReader.readLine()) != null) {
						if(offset == index) {
							ParsedData collectedWords = gson.fromJson(line, ParsedData.class);
							for(CollectedWord entry : collectedWords.getWords()) {
								entry.setOffset(offset);
							}
							Query check = new Query();
							check.addCriteria(Criteria.where("offset").is(offset));
							if(operations.exists(check, CollectedWord.class)) {
								offsetBean = operations.findOne(query, Offset.class);
							}else{
								operations.insertAll(collectedWords.getWords());
								offsetBean = operations.findAndModify(query, update, Offset.class);
							}
							offset = offsetBean.getValue().doubleValue()+1;
						}
						index++;
					}
					log.log(Level.FINE, "Finished with offset: " + offset);
				}finally {
					if(isData != null)
						isData.close();
				}
			}else if("read".equals(args[0])) {
				if(args.length > 1) {
					String word = args[1];
					log.log(Level.INFO, "Started Processor in Read Mode for get hits of word: " + word);
					PopularWord popularWord = popularWordService.findByWord(word);
					log.log(Level.INFO, "The hits for popular word \"" + word + "\" it's " + popularWord.getHits());
				}else{
					log.log(Level.INFO, "Started Processor in Read Mode for get all words related hits");
					for(PopularWord entry : popularWordService.findAll()) {
						log.log(Level.INFO, "The hits for popular word \"" + entry.getWord() + "\" it's " + entry.getHits());
					}
				}
			}else{
				log.log(Level.SEVERE, "Command arguments not recognized: use write, elaborate or read mode");
			}
		}else{
			log.log(Level.SEVERE, "Empty arguments: use write, elaborate or read mode");
		}
	}

}
