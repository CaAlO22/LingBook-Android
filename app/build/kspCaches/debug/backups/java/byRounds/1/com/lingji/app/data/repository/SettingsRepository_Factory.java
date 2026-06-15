package com.lingji.app.data.repository;

import com.lingji.app.data.db.dao.SettingsDao;
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
public final class SettingsRepository_Factory implements Factory<SettingsRepository> {
  private final Provider<SettingsDao> settingsDaoProvider;

  public SettingsRepository_Factory(Provider<SettingsDao> settingsDaoProvider) {
    this.settingsDaoProvider = settingsDaoProvider;
  }

  @Override
  public SettingsRepository get() {
    return newInstance(settingsDaoProvider.get());
  }

  public static SettingsRepository_Factory create(Provider<SettingsDao> settingsDaoProvider) {
    return new SettingsRepository_Factory(settingsDaoProvider);
  }

  public static SettingsRepository newInstance(SettingsDao settingsDao) {
    return new SettingsRepository(settingsDao);
  }
}
