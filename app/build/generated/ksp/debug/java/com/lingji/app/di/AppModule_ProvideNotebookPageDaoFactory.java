package com.lingji.app.di;

import com.lingji.app.data.db.LingjiDatabase;
import com.lingji.app.data.db.dao.NotebookPageDao;
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
public final class AppModule_ProvideNotebookPageDaoFactory implements Factory<NotebookPageDao> {
  private final Provider<LingjiDatabase> databaseProvider;

  public AppModule_ProvideNotebookPageDaoFactory(Provider<LingjiDatabase> databaseProvider) {
    this.databaseProvider = databaseProvider;
  }

  @Override
  public NotebookPageDao get() {
    return provideNotebookPageDao(databaseProvider.get());
  }

  public static AppModule_ProvideNotebookPageDaoFactory create(
      Provider<LingjiDatabase> databaseProvider) {
    return new AppModule_ProvideNotebookPageDaoFactory(databaseProvider);
  }

  public static NotebookPageDao provideNotebookPageDao(LingjiDatabase database) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideNotebookPageDao(database));
  }
}
