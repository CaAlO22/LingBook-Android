package com.lingji.app.data.remote;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

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
public final class LLMService_Factory implements Factory<LLMService> {
  @Override
  public LLMService get() {
    return newInstance();
  }

  public static LLMService_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static LLMService newInstance() {
    return new LLMService();
  }

  private static final class InstanceHolder {
    private static final LLMService_Factory INSTANCE = new LLMService_Factory();
  }
}
