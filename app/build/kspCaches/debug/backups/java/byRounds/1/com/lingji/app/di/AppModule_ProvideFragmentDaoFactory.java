package com.lingji.app.di;

import com.lingji.app.data.db.LingjiDatabase;
import com.lingji.app.data.db.dao.FragmentDao;
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
public final class AppModule_ProvideFragmentDaoFactory implements Factory<FragmentDao> {
  private final Provider<LingjiDatabase> databaseProvider;

  public AppModule_ProvideFragmentDaoFactory(Provider<LingjiDatabase> databaseProvider) {
    this.databaseProvider = databaseProvider;
  }

  @Override
  public FragmentDao get() {
    return provideFragmentDao(databaseProvider.get());
  }

  public static AppModule_ProvideFragmentDaoFactory create(
      Provider<LingjiDatabase> databaseProvider) {
    return new AppModule_ProvideFragmentDaoFactory(databaseProvider);
  }

  public static FragmentDao provideFragmentDao(LingjiDatabase database) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideFragmentDao(database));
  }
}
