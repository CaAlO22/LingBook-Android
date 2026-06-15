package com.lingji.app.di;

import com.lingji.app.data.db.LingjiDatabase;
import com.lingji.app.data.db.dao.SettingsDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
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
public final class AppModule_ProvideSettingsDaoFactory implements Factory<SettingsDao> {
  private final Provider<LingjiDatabase> databaseProvider;

  public AppModule_ProvideSettingsDaoFactory(Provider<LingjiDatabase> databaseProvider) {
    this.databaseProvider = databaseProvider;
  }

  @Override
  public SettingsDao get() {
    return provideSettingsDao(databaseProvider.get());
  }

  public static AppModule_ProvideSettingsDaoFactory create(
      Provider<LingjiDatabase> databaseProvider) {
    return new AppModule_ProvideSettingsDaoFactory(databaseProvider);
  }

  public static SettingsDao provideSettingsDao(LingjiDatabase database) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideSettingsDao(database));
  }
}
