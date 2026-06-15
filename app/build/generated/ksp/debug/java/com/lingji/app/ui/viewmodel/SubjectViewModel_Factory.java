package com.lingji.app.ui.viewmodel;

import com.lingji.app.data.file.FileManager;
import com.lingji.app.data.remote.IndexService;
import com.lingji.app.data.remote.LLMService;
import com.lingji.app.data.repository.SettingsRepository;
import com.lingji.app.data.repository.SubjectRepository;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
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
public final class SubjectViewModel_Factory implements Factory<SubjectViewModel> {
  private final Provider<SubjectRepository> subjectRepositoryProvider;

  private final Provider<SettingsRepository> settingsRepositoryProvider;

  private final Provider<LLMService> llmServiceProvider;

  private final Provider<IndexService> indexServiceProvider;

  private final Provider<FileManager> fileManagerProvider;

  public SubjectViewModel_Factory(Provider<SubjectRepository> subjectRepositoryProvider,
      Provider<SettingsRepository> settingsRepositoryProvider,
      Provider<LLMService> llmServiceProvider, Provider<IndexService> indexServiceProvider,
      Provider<FileManager> fileManagerProvider) {
    this.subjectRepositoryProvider = subjectRepositoryProvider;
    this.settingsRepositoryProvider = settingsRepositoryProvider;
    this.llmServiceProvider = llmServiceProvider;
    this.indexServiceProvider = indexServiceProvider;
    this.fileManagerProvider = fileManagerProvider;
  }

  @Override
  public SubjectViewModel get() {
    return newInstance(subjectRepositoryProvider.get(), settingsRepositoryProvider.get(), llmServiceProvider.get(), indexServiceProvider.get(), fileManagerProvider.get());
  }

  public static SubjectViewModel_Factory create(
      Provider<SubjectRepository> subjectRepositoryProvider,
      Provider<SettingsRepository> settingsRepositoryProvider,
      Provider<LLMService> llmServiceProvider, Provider<IndexService> indexServiceProvider,
      Provider<FileManager> fileManagerProvider) {
    return new SubjectViewModel_Factory(subjectRepositoryProvider, settingsRepositoryProvider, llmServiceProvider, indexServiceProvider, fileManagerProvider);
  }

  public static SubjectViewModel newInstance(SubjectRepository subjectRepository,
      SettingsRepository settingsRepository, LLMService llmService, IndexService indexService,
      FileManager fileManager) {
    return new SubjectViewModel(subjectRepository, settingsRepository, llmService, indexService, fileManager);
  }
}
