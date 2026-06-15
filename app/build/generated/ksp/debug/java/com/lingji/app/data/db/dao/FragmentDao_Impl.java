package com.lingji.app.data.db.dao;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.room.util.StringUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.lingji.app.data.db.entities.FragmentEntity;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.StringBuilder;
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
public final class FragmentDao_Impl implements FragmentDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<FragmentEntity> __insertionAdapterOfFragmentEntity;

  private final EntityDeletionOrUpdateAdapter<FragmentEntity> __deletionAdapterOfFragmentEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteBySubject;

  private final SharedSQLiteStatement __preparedStmtOfUpdateContent;

  public FragmentDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfFragmentEntity = new EntityInsertionAdapter<FragmentEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `fragments` (`id`,`subjectId`,`content`,`timestamp`,`isUnmerged`) VALUES (?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final FragmentEntity entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getSubjectId());
        statement.bindString(3, entity.getContent());
        statement.bindLong(4, entity.getTimestamp());
        final int _tmp = entity.isUnmerged() ? 1 : 0;
        statement.bindLong(5, _tmp);
      }
    };
    this.__deletionAdapterOfFragmentEntity = new EntityDeletionOrUpdateAdapter<FragmentEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `fragments` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final FragmentEntity entity) {
        statement.bindString(1, entity.getId());
      }
    };
    this.__preparedStmtOfDeleteBySubject = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM fragments WHERE subjectId = ?";
        return _query;
      }
    };
    this.__preparedStmtOfUpdateContent = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE fragments SET content = ? WHERE id = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insert(final FragmentEntity fragment,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfFragmentEntity.insert(fragment);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insertAll(final List<FragmentEntity> fragments,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfFragmentEntity.insert(fragments);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object delete(final FragmentEntity fragment,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfFragmentEntity.handle(fragment);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteBySubject(final String subjectId,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteBySubject.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, subjectId);
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
          __preparedStmtOfDeleteBySubject.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object updateContent(final String id, final String content,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateContent.acquire();
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
          __preparedStmtOfUpdateContent.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<FragmentEntity>> getFragmentsBySubject(final String subjectId) {
    final String _sql = "SELECT * FROM fragments WHERE subjectId = ? ORDER BY timestamp ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, subjectId);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"fragments"}, new Callable<List<FragmentEntity>>() {
      @Override
      @NonNull
      public List<FragmentEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSubjectId = CursorUtil.getColumnIndexOrThrow(_cursor, "subjectId");
          final int _cursorIndexOfContent = CursorUtil.getColumnIndexOrThrow(_cursor, "content");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfIsUnmerged = CursorUtil.getColumnIndexOrThrow(_cursor, "isUnmerged");
          final List<FragmentEntity> _result = new ArrayList<FragmentEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final FragmentEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpSubjectId;
            _tmpSubjectId = _cursor.getString(_cursorIndexOfSubjectId);
            final String _tmpContent;
            _tmpContent = _cursor.getString(_cursorIndexOfContent);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final boolean _tmpIsUnmerged;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsUnmerged);
            _tmpIsUnmerged = _tmp != 0;
            _item = new FragmentEntity(_tmpId,_tmpSubjectId,_tmpContent,_tmpTimestamp,_tmpIsUnmerged);
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
  public Object getFragmentsBySubjectOnce(final String subjectId,
      final Continuation<? super List<FragmentEntity>> $completion) {
    final String _sql = "SELECT * FROM fragments WHERE subjectId = ? ORDER BY timestamp ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, subjectId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<FragmentEntity>>() {
      @Override
      @NonNull
      public List<FragmentEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSubjectId = CursorUtil.getColumnIndexOrThrow(_cursor, "subjectId");
          final int _cursorIndexOfContent = CursorUtil.getColumnIndexOrThrow(_cursor, "content");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfIsUnmerged = CursorUtil.getColumnIndexOrThrow(_cursor, "isUnmerged");
          final List<FragmentEntity> _result = new ArrayList<FragmentEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final FragmentEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpSubjectId;
            _tmpSubjectId = _cursor.getString(_cursorIndexOfSubjectId);
            final String _tmpContent;
            _tmpContent = _cursor.getString(_cursorIndexOfContent);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final boolean _tmpIsUnmerged;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsUnmerged);
            _tmpIsUnmerged = _tmp != 0;
            _item = new FragmentEntity(_tmpId,_tmpSubjectId,_tmpContent,_tmpTimestamp,_tmpIsUnmerged);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getUnmergedFragments(final String subjectId,
      final Continuation<? super List<FragmentEntity>> $completion) {
    final String _sql = "SELECT * FROM fragments WHERE subjectId = ? AND isUnmerged = 1 ORDER BY timestamp ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, subjectId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<FragmentEntity>>() {
      @Override
      @NonNull
      public List<FragmentEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSubjectId = CursorUtil.getColumnIndexOrThrow(_cursor, "subjectId");
          final int _cursorIndexOfContent = CursorUtil.getColumnIndexOrThrow(_cursor, "content");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfIsUnmerged = CursorUtil.getColumnIndexOrThrow(_cursor, "isUnmerged");
          final List<FragmentEntity> _result = new ArrayList<FragmentEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final FragmentEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpSubjectId;
            _tmpSubjectId = _cursor.getString(_cursorIndexOfSubjectId);
            final String _tmpContent;
            _tmpContent = _cursor.getString(_cursorIndexOfContent);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final boolean _tmpIsUnmerged;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsUnmerged);
            _tmpIsUnmerged = _tmp != 0;
            _item = new FragmentEntity(_tmpId,_tmpSubjectId,_tmpContent,_tmpTimestamp,_tmpIsUnmerged);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteUnmergedByIds(final String subjectId, final List<String> ids,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final StringBuilder _stringBuilder = StringUtil.newStringBuilder();
        _stringBuilder.append("DELETE FROM fragments WHERE subjectId = ");
        _stringBuilder.append("?");
        _stringBuilder.append(" AND isUnmerged = 1 AND id IN (");
        final int _inputSize = ids.size();
        StringUtil.appendPlaceholders(_stringBuilder, _inputSize);
        _stringBuilder.append(")");
        final String _sql = _stringBuilder.toString();
        final SupportSQLiteStatement _stmt = __db.compileStatement(_sql);
        int _argIndex = 1;
        _stmt.bindString(_argIndex, subjectId);
        _argIndex = 2;
        for (String _item : ids) {
          _stmt.bindString(_argIndex, _item);
          _argIndex++;
        }
        __db.beginTransaction();
        try {
          _stmt.executeUpdateDelete();
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
