package com.promptscanner.backend;

import com.promptscanner.backend.entity.HeuristicRule;
import com.promptscanner.backend.repository.HeuristicRuleRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.List;

@SpringBootApplication
public class BackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

	@Bean
	public CommandLineRunner initDatabase(HeuristicRuleRepository repository) {
		return args -> {
			if (repository.count() == 0) {
				List<HeuristicRule> defaultRules = List.of(
						new HeuristicRule("ignore all previous instructions", false, true),
						new HeuristicRule("system message", false, true),
						new HeuristicRule("you are now", false, true),
						new HeuristicRule("system prompt", false, true),
						new HeuristicRule("do not follow the rules", false, true),
						new HeuristicRule("forget everything", false, true),
						new HeuristicRule("bypass", false, true),
						new HeuristicRule("new instructions", false, true),
						new HeuristicRule("act as a", false, true),
						new HeuristicRule("jailbreak", false, true)
				);
				repository.saveAll(defaultRules);
				System.out.println("Initialized database with 10 default heuristic rules.");
			}
		};
	}
}
