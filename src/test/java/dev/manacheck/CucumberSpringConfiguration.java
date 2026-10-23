package dev.manacheck;

import io.cucumber.spring.CucumberContextConfiguration;

/** Reuses the fixture-only PostgreSQL 17 datasource and disabled demo/provider configuration. */
@CucumberContextConfiguration
public class CucumberSpringConfiguration extends PostgresTestBase {}
