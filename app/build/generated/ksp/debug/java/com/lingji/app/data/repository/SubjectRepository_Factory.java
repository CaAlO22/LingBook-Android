package com.lingji.app.data.repository;

import com.lingji.app.data.db.dao.FragmentDao;
import com.lingji.app.data.db.dao.NotebookPageDao;
import com.lingji.app.data.db.dao.SubjectDao;
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
public final class SubjectRepository_Factory implements Factory<SubjectRepository> {
  private final Provider<SubjectDao> subjectDaoProvider;

  private final Provider<FragmentDao> fragmentDaoProvider;

  private final Provider<NotebookPageDao> pageDaoProvider;

  public SubjectRepository_Factory(Provider<SubjectDao> subjectDaoProvider,
      Provider<FragmentDao> fragmentDaoProvider, Provider<NotebookPageDao> pageDaoProvider) {
    this.subjectDaoProvider = subjectDaoProvider;
    this.fragmentDaoProvider = fragmentDaoProvider;
    this.pageDaoProvider = pageDaoProvider;
  }

  @Override
  public SubjectRepository get() {
    return newInstance(subjectDaoProvider.get(), fragmentDaoProvider.get(), pageDaoProvider.get());
  }

  public static SubjectRepository_Factory create(Provider<SubjectDao> subjectDaoProvider,
      Provider<FragmentDao> fragmentDaoProvider, Provider<NotebookPageDao> pageDaoProvider) {
    return new SubjectRepository_Factory(subjectDaoProvider, fragmentDaoProvider, pageDaoProvider);
  }

  public static SubjectRepository newInstance(SubjectDao subjectDao, FragmentDao fragmentDao,
      NotebookPageDao pageDao) {
    return new SubjectRepository(subjectDao, fragmentDao, pageDao);
  }
}
