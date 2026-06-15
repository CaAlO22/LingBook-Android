package com.lingji.app.data.db.dao;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomDatabaseKt;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.lingji.app.data.db.entities.SubjectEntity;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class SubjectDao_Impl implements SubjectDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<SubjectEntity> __insertionAdapterOfSubjectEntity;

  private final EntityDeletionOrUpdateAdapter<SubjectEntity> __deletionAdapterOfSubjectEntity;

  private final SharedSQLiteStatement __preparedStmtOfRename;

  private final SharedSQLiteStatement __preparedStmtOfUpdateAggregatedNote;

  private final SharedSQLiteStatement __preparedStmtOfUpdateStudyPlan;

  private final SharedSQLiteStatement __preparedStmtOfUpdateOrderIndex;

  private final SharedSQLiteStatement __preparedStmtOfUpdatePageIndexJson;

  public SubjectDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfSubjectEntity = new EntityInsertionAdapter<SubjectEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `subjects` (`id`,`title`,`type`,`aggregatedNote`,`prevAggregatedNote`,`studyPlan`,`createdAt`,`orderIndex`,`pageIndexJson`) VALUES (?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final SubjectEntity entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getTitle());
        statement.bindString(3, entity.getType());
        statement.bindString(4, entity.getAggregatedNote());
        if (entity.getPrevAggregatedNote() == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.getPrevAggregatedNote());
        }
        statement.bindString(6, entity.getStudyPlan());
        statement.bindLong(7, entity.getCreatedAt());
        statement.bindLong(8, entity.getOrderIndex());
        statement.bindString(9, entity.getPageIndexJson());
      }
    };
    this.__deletionAdapterOfSubjectEntity = new EntityDeletionOrUpdateAdapter<SubjectEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `subjects` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final SubjectEntity entity) {
        statement.bindString(1, entity.getId());
      }
    };
    this.__preparedStmtOfRename = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE subjects SET title = ? WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfUpdateAggregatedNote = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE subjects SET aggregatedNote = ?, prevAggregatedNote = ? WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfUpdateStudyPlan = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE subjects SET studyPlan = ? WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfUpdateOrderIndex = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE subjects SET orderIndex = ? WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfUpdatePageIndexJson = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE subjects SET pageIndexJson = ? WHERE id = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insert(final SubjectEntity subject, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfSubjectEntity.insert(subject);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object delete(final SubjectEntity subject, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfSubjectEntity.handle(subject);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object upsert(final SubjectEntity subject, final Continuation<? super Unit> $completion) {
    return RoomDatabaseKt.withTransaction(__db, (__cont) -> SubjectDao.DefaultImpls.upsert(SubjectDao_Impl.this, subject, __cont), $completion);
  }

  @Override
  public Object rename(final String id, final String title,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfRename.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, title);
        _argIndex = 2;
        _stmt.bindString(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfRename.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object updateAggregatedNote(final String id, final String content, final String prev,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateAggregatedNote.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, content);
        _argIndex = 2;
        if (prev == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindString(_argIndex, prev);
        }
        _argIndex = 3;
        _stmt.bindString(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfUpdateAggregatedNote.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object updateStudyPlan(final String id, final String content,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateStudyPlan.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, content);
        _argIndex = 2;
        _stmt.bindString(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfUpdateStudyPlan.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object updateOrderIndex(final String id, final int orderIndex,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateOrderIndex.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, orderIndex);
        _argIndex = 2;
        _stmt.bindString(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfUpdateOrderIndex.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object updatePageIndexJson(final String id, final String json,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdatePageIndexJson.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, json);
        _argIndex = 2;
        _stmt.bindString(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfUpdatePageIndexJson.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<SubjectEntity>> getAllSubjects() {
    final String _sql = "SELECT * FROM subjects ORDER BY orderIndex DESC, createdAt DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"subjects"}, new Callable<List<SubjectEntity>>() {
      @Override
      @NonNull
      public List<SubjectEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
          final int _cursorIndexOfAggregatedNote = CursorUtil.getColumnIndexOrThrow(_cursor, "aggregatedNote");
          final int _cursorIndexOfPrevAggregatedNote = CursorUtil.getColumnIndexOrThrow(_cursor, "prevAggregatedNote");
          final int _cursorIndexOfStudyPlan = CursorUtil.getColumnIndexOrThrow(_cursor, "studyPlan");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfOrderIndex = CursorUtil.getColumnIndexOrThrow(_cursor, "orderIndex");
          final int _cursorIndexOfPageIndexJson = CursorUtil.getColumnIndexOrThrow(_cursor, "pageIndexJson");
          final List<SubjectEntity> _result = new ArrayList<SubjectEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final SubjectEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final String _tmpType;
            _tmpType = _cursor.getString(_cursorIndexOfType);
            final String _tmpAggregatedNote;
            _tmpAggregatedNote = _cursor.getString(_cursorIndexOfAggregatedNote);
            final String _tmpPrevAggregatedNote;
            if (_cursor.isNull(_cursorIndexOfPrevAggregatedNote)) {
              _tmpPrevAggregatedNote = null;
            } else {
              _tmpPrevAggregatedNote = _cursor.getString(_cursorIndexOfPrevAggregatedNote);
            }
            final String _tmpStudyPlan;
            _tmpStudyPlan = _cursor.getString(_cursorIndexOfStudyPlan);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final int _tmpOrderIndex;
            _tmpOrderIndex = _cursor.getInt(_cursorIndexOfOrderIndex);
            final String _tmpPageIndexJson;
            _tmpPageIndexJson = _cursor.getString(_cursorIndexOfPageIndexJson);
            _item = new SubjectEntity(_tmpId,_tmpTitle,_tmpType,_tmpAggregatedNote,_tmpPrevAggregatedNote,_tmpStudyPlan,_tmpCreatedAt,_tmpOrderIndex,_tmpPageIndexJson);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object getSubjectById(final String id,
      final Continuation<? super SubjectEntity> $completion) {
    final String _sql = "SELECT * FROM subjects WHERE id = ? LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<SubjectEntity>() {
      @Override
      @Nullable
      public SubjectEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
          final int _cursorIndexOfAggregatedNote = CursorUtil.getColumnIndexOrThrow(_cursor, "aggregatedNote");
          final int _cursorIndexOfPrevAggregatedNote = CursorUtil.getColumnIndexOrThrow(_cursor, "prevAggregatedNote");
          final int _cursorIndexOfStudyPlan = CursorUtil.getColumnIndexOrThrow(_cursor, "studyPlan");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfOrderIndex = CursorUtil.getColumnIndexOrThrow(_cursor, "orderIndex");
          final int _cursorIndexOfPageIndexJson = CursorUtil.getColumnIndexOrThrow(_cursor, "pageIndexJson");
          final SubjectEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final String _tmpType;
            _tmpType = _cursor.getString(_cursorIndexOfType);
            final String _tmpAggregatedNote;
            _tmpAggregatedNote = _cursor.getString(_cursorIndexOfAggregatedNote);
            final String _tmpPrevAggregatedNote;
            if (_cursor.isNull(_cursorIndexOfPrevAggregatedNote)) {
              _tmpPrevAggregatedNote = null;
            } else {
              _tmpPrevAggregatedNote = _cursor.getString(_cursorIndexOfPrevAggregatedNote);
            }
            final String _tmpStudyPlan;
            _tmpStudyPlan = _cursor.getString(_cursorIndexOfStudyPlan);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final int _tmpOrderIndex;
            _tmpOrderIndex = _cursor.getInt(_cursorIndexOfOrderIndex);
            final String _tmpPageIndexJson;
            _tmpPageIndexJson = _cursor.getString(_cursorIndexOfPageIndexJson);
            _result = new SubjectEntity(_tmpId,_tmpTitle,_tmpType,_tmpAggregatedNote,_tmpPrevAggregatedNote,_tmpStudyPlan,_tmpCreatedAt,_tmpOrderIndex,_tmpPageIndexJson);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
