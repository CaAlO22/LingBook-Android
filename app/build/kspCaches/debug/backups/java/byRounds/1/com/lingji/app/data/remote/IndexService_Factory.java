package com.lingji.app.data.remote;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava"
})
public final class IndexService_Factory implements Factory<IndexService> {
  private final Provider<LLMService> llmServiceProvider;

  public IndexService_Factory(Provider<LLMService> llmServiceProvider) {
    this.llmServiceProvider = llmServiceProvider;
  }

  @Override
  public IndexService get() {
    return newInstance(llmServiceProvider.get());
  }

  public static IndexService_Factory create(Provider<LLMService> llmServiceProvider) {
    return new IndexService_Factory(llmServiceProvider);
  }

  public static IndexService newInstance(LLMService llmService) {
    return new IndexService(llmService);
  }
}
