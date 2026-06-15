package com.lingji.app.di;

import com.lingji.app.data.db.LingjiDatabase;
import com.lingji.app.data.db.dao.SubjectDao;
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
public final class AppModule_ProvideSubjectDaoFactory implements Factory<SubjectDao> {
  private final Provider<LingjiDatabase> databaseProvider;

  public AppModule_ProvideSubjectDaoFactory(Provider<LingjiDatabase> databaseProvider) {
    this.databaseProvider = databaseProvider;
  }

  @Override
  public SubjectDao get() {
    return provideSubjectDao(databaseProvider.get());
  }

  public static AppModule_ProvideSubjectDaoFactory create(
      Provider<LingjiDatabase> databaseProvider) {
    return new AppModule_ProvideSubjectDaoFactory(databaseProvider);
  }

  public static SubjectDao provideSubjectDao(LingjiDatabase database) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideSubjectDao(database));
  }
}
