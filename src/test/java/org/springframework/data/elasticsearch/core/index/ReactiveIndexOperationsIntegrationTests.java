/*
 * Copyright 2018-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.elasticsearch.core.index;

import static org.assertj.core.api.Assertions.*;
import static org.skyscreamer.jsonassert.JSONAssert.*;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDate;
import java.util.Set;

import org.json.JSONException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.NewElasticsearchClientDevelopment;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Mapping;
import org.springframework.data.elasticsearch.annotations.Setting;
import org.springframework.data.elasticsearch.core.AbstractReactiveElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.ReactiveElasticsearchOperations;
import org.springframework.data.elasticsearch.core.ReactiveIndexOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.junit.jupiter.SpringIntegrationTest;
import org.springframework.data.elasticsearch.utils.IndexNameProvider;
import org.springframework.lang.Nullable;

/**
 * @author Peter-Josef Meisch
 */
@SpringIntegrationTest
public abstract class ReactiveIndexOperationsIntegrationTests implements NewElasticsearchClientDevelopment {

	@Autowired private ReactiveElasticsearchOperations operations;
	@Autowired private IndexNameProvider indexNameProvider;
	private ReactiveIndexOperations indexOperations;

	boolean rhlcWithCluster8() {
		var clusterVersion = ((AbstractReactiveElasticsearchTemplate) operations).getClusterVersion().block();
		return (oldElasticsearchClient() && clusterVersion != null && clusterVersion.startsWith("8"));
	}

	@BeforeEach
	void setUp() {
		indexNameProvider.increment();
		indexOperations = operations.indexOps(IndexCoordinates.of(indexNameProvider.indexName()));
	}

	@Test
	@Order(java.lang.Integer.MAX_VALUE)
	void cleanup() {
		operations.indexOps(IndexCoordinates.of(indexNameProvider.getPrefix() + "*")).delete().block();
	}

	@Test // DATAES-678
	void shouldCreateIndexOpsForIndexCoordinates() {

		ReactiveIndexOperations indexOperations = operations.indexOps(IndexCoordinates.of("some-index-name"));

		assertThat(indexOperations).isNotNull();
	}

	@Test // DATAES-678
	void shouldCreateIndexOpsForEntityClass() {

		ReactiveIndexOperations indexOperations = operations.indexOps(Entity.class);

		assertThat(indexOperations).isNotNull();
	}

	@Test // DATAES-678
	void shouldCreateIndexForName() {

		indexOperations.create() //
				.as(StepVerifier::create) //
				.expectNext(true) //
				.verifyComplete();
	}

	@Test // DATAES-678
	void shouldCreateIndexForEntity() {

		ReactiveIndexOperations indexOps = operations.indexOps(Entity.class);

		indexOps.create() //
				.as(StepVerifier::create) //
				.expectNext(true) //
				.verifyComplete();

		// check the settings from the class annotation
		indexOps.getSettings().as(StepVerifier::create).consumeNextWith(settings -> {
			assertThat(settings.get("index.number_of_replicas")).isEqualTo("2");
			assertThat(settings.get("index.number_of_shards")).isEqualTo("3");
			assertThat(settings.get("index.refresh_interval")).isEqualTo("4s");
		}).verifyComplete();
	}

	@Test // DATAES-678
	void shouldCreateIndexWithGivenSettings() {

		var index = new Settings() //
				.append("number_of_replicas", 3) //
				.append("number_of_shards", 4)//
				.append("refresh_interval", "5s");
		var requiredSettings = new Settings().append("index", index);

		indexOperations.create(requiredSettings) //
				.as(StepVerifier::create) //
				.expectNext(true) //
				.verifyComplete();

		indexOperations.getSettings().as(StepVerifier::create).consumeNextWith(settings -> {
			var flattened = settings.flatten();
			assertThat(flattened.get("index.number_of_replicas")).isEqualTo("3");
			assertThat(flattened.get("index.number_of_shards")).isEqualTo("4");
			assertThat(flattened.get("index.refresh_interval")).isEqualTo("5s");
		}).verifyComplete();
	}

	@Test // DATAES-678
	void shouldCreateIndexWithAnnotatedSettings() {

		ReactiveIndexOperations indexOps = operations.indexOps(EntityWithAnnotatedSettingsAndMappings.class);

		indexOps.create() //
				.as(StepVerifier::create) //
				.expectNext(true) //
				.verifyComplete();

		indexOps.getSettings().as(StepVerifier::create).consumeNextWith(settings -> {
			assertThat(settings.get("index.number_of_replicas")).isEqualTo("0");
			assertThat(settings.get("index.number_of_shards")).isEqualTo("1");
			assertThat(settings.get("index.analysis.analyzer.emailAnalyzer.tokenizer")).isNotNull();
			assertThat(settings.get("index.analysis.analyzer.emailAnalyzer.tokenizer")).isEqualTo("uax_url_email");
		}).verifyComplete();
	}

	@Test // DATAES-678
	public void shouldCreateIndexUsingServerDefaultConfiguration() {

		indexOperations.create() //
				.as(StepVerifier::create) //
				.expectNext(true) //
				.verifyComplete();

		// check the settings from the class annotation
		indexOperations.getSettings().as(StepVerifier::create).consumeNextWith(settings -> {
			assertThat(settings.get("index.number_of_replicas")).isEqualTo("1");
			assertThat(settings.get("index.number_of_shards")).isEqualTo("1");
		}).verifyComplete();
	}

	@Test // DATAES-678
	void shouldDeleteIfItExists() {

		indexOperations.create().block();

		indexOperations.delete() //
				.as(StepVerifier::create) //
				.expectNext(true) //
				.verifyComplete();
	}

	@Test // DATAES-678
	void shouldReturnFalseOnDeleteIfItDoesNotExist() {

		indexOperations.delete() //
				.as(StepVerifier::create) //
				.expectNext(false) //
				.verifyComplete();
	}

	@Test // DATAES-678
	void shouldReturnExistsTrueIfIndexDoesExist() {

		indexOperations.create().block();

		indexOperations.exists() //
				.as(StepVerifier::create) //
				.expectNext(true) //
				.verifyComplete();
	}

	@Test // DATAES-678
	void shouldReturnExistsFalseIfIndexDoesNotExist() {

		indexOperations.exists() //
				.as(StepVerifier::create) //
				.expectNext(false) //
				.verifyComplete();
	}

	@Test // DATAES-678
	void shouldCreateMappingForEntityFromProperties() {

		String expected = """
				{
				  "properties":{
				    "text": {
				      "type": "text"
				    },
				    "publication-date": {
				      "type": "date",
				      "format": "basic_date"
				    }
				  }
				}
				"""; //

		indexOperations.createMapping(Entity.class) //
				.as(StepVerifier::create) //
				.assertNext(document -> {
					try {
						assertEquals(expected, document.toJson(), false);
					} catch (JSONException e) {
						fail("", e);
					}
				}) //
				.verifyComplete();
	}

	@Test // DATAES-678
	void shouldCreateMappingForEntityFromMappingAnnotation() {

		String expected = """
				{
				  "properties": {
				    "email": {
				      "type": "text",
				      "analyzer": "emailAnalyzer"
				    }
				  }
				}
				"""; //

		indexOperations.createMapping(EntityWithAnnotatedSettingsAndMappings.class) //
				.as(StepVerifier::create) //
				.assertNext(document -> {
					try {
						assertEquals(expected, document.toJson(), false);
					} catch (JSONException e) {
						fail("", e);
					}
				}) //
				.verifyComplete();
	}

	@Test // DATAES-678
	void shouldCreateMappingBoundEntity() {
		ReactiveIndexOperations indexOps = operations.indexOps(Entity.class);

		String expected = """
				{
				  "properties":{
				    "text": {
				      "type": "text"
				    },
				    "publication-date": {
				      "type": "date",
				      "format": "basic_date"
				    }
				  }
				}
				"""; //

		indexOps.createMapping() //
				.as(StepVerifier::create) //
				.assertNext(document -> {
					try {
						assertEquals(expected, document.toJson(), false);
					} catch (JSONException e) {
						fail("", e);
					}
				}) //
				.verifyComplete();
	}

	@Test // DATAES-678
	void shouldPutAndGetMapping() {

		ReactiveIndexOperations indexOps = operations.indexOps(Entity.class);

		String expected = """
				{
				  "properties":{
				    "text": {
				      "type": "text"
				    },
				    "publication-date": {
				      "type": "date",
				      "format": "basic_date"
				    }
				  }
				}
				"""; //

		indexOps.create() //
				.then(indexOps.putMapping()) //
				.then(indexOps.getMapping()) //
				.as(StepVerifier::create) //
				.assertNext(document -> {
					try {
						assertEquals(expected, document.toJson(), false);
					} catch (JSONException e) {
						fail("", e);
					}
				}).verifyComplete();
	}

	@Test // DATAES-864
	void shouldCreateAlias() {

		AliasActions aliasActions = new AliasActions();
		aliasActions.add(new AliasAction.Add(AliasActionParameters.builder()
				.withIndices(indexOperations.getIndexCoordinates().getIndexNames()).withAliases("aliasA", "aliasB").build()));

		indexOperations.create().flatMap(success -> {
			if (success) {
				return indexOperations.alias(aliasActions);
			} else {
				return Mono.just(false);
			}
		}).as(StepVerifier::create).expectNext(true).verifyComplete();
	}

	@Test // DATAES-864
	void shouldGetAliasData() {

		AliasActions aliasActions = new AliasActions();
		aliasActions.add(new AliasAction.Add(AliasActionParameters.builder()
				.withIndices(indexOperations.getIndexCoordinates().getIndexNames()).withAliases("aliasA", "aliasB").build()));

		assertThat(indexOperations.create().block()).isTrue();
		assertThat(indexOperations.alias(aliasActions).block()).isTrue();

		indexOperations.getAliases("aliasA") //
				.as(StepVerifier::create) //
				.assertNext(aliasDatas -> { //
					Set<AliasData> aliasData = aliasDatas.get(indexOperations.getIndexCoordinates().getIndexName());
					assertThat(aliasData.stream().map(AliasData::getAlias)).containsExactly("aliasA");
				}) //
				.verifyComplete();
	}

	@Document(indexName = "#{@indexNameProvider.indexName()}")
	@Setting(shards = 3, replicas = 2, refreshInterval = "4s")
	static class Entity {
		@Nullable
		@Id private String id;
		@Nullable
		@Field(type = FieldType.Text) private String text;
		@Nullable
		@Field(name = "publication-date", type = FieldType.Date,
				format = DateFormat.basic_date) private LocalDate publicationDate;

		@Nullable
		public String getId() {
			return id;
		}

		public void setId(@Nullable String id) {
			this.id = id;
		}

		@Nullable
		public String getText() {
			return text;
		}

		public void setText(@Nullable String text) {
			this.text = text;
		}

		@Nullable
		public LocalDate getPublicationDate() {
			return publicationDate;
		}

		public void setPublicationDate(@Nullable LocalDate publicationDate) {
			this.publicationDate = publicationDate;
		}
	}

	@Document(indexName = "#{@indexNameProvider.indexName()}")
	@Setting(settingPath = "/settings/test-settings.json")
	@Mapping(mappingPath = "/mappings/test-mappings.json")
	static class EntityWithAnnotatedSettingsAndMappings {
		@Nullable
		@Id private String id;

		@Nullable
		public String getId() {
			return id;
		}

		public void setId(@Nullable String id) {
			this.id = id;
		}
	}
}
