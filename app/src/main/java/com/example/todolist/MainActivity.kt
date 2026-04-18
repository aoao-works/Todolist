package com.example.todolist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.time.LocalDate

// 1. データモデル (Roomのテーブル設計図にアップグレード)
@Entity(tableName = "todo_table")
data class TodoItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0, // 自動で連番を振る
    val title: String,
    var isDone: Boolean = false,
    val isDaily: Boolean = false,
    var lastCompletedDate: Long? = null
)

// 2. DAO (データベースへの命令)
@Dao
interface TodoDao {
    @Query("SELECT * FROM todo_table ORDER BY id ASC")
    fun getAllTodos(): Flow<List<TodoItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTodo(todo: TodoItem)

    @Update
    suspend fun updateTodo(todo: TodoItem)

    @Delete
    suspend fun deleteTodo(todo: TodoItem)
}

// 3. データベース本体
@Database(entities = [TodoItem::class], version = 1, exportSchema = false)
abstract class TodoDatabase : RoomDatabase() {
    abstract fun todoDao(): TodoDao
}

// 4. ViewModel (データベースとやり取りするように変更)
class TodoViewModel(private val dao: TodoDao) : ViewModel() {
    // データベースから常に最新のリストを受け取る
    val todoList: Flow<List<TodoItem>> = dao.getAllTodos()

    var isDeleteMode by mutableStateOf(false)
    var selectedForDeletion = mutableStateListOf<Int>()
    var showAddDialog by mutableStateOf(false)

    fun checkAndResetDailyTasks(todos: List<TodoItem>) {
        viewModelScope.launch {
            val today = LocalDate.now().toEpochDay()
            todos.forEach { item ->
                if (item.isDaily && item.isDone && item.lastCompletedDate != today) {
                    dao.updateTodo(item.copy(isDone = false, lastCompletedDate = null))
                }
            }
        }
    }

    fun addTodo(title: String, isDaily: Boolean) {
        if (title.isNotBlank()) {
            viewModelScope.launch {
                dao.insertTodo(TodoItem(title = title, isDaily = isDaily))
            }
        }
    }

    fun toggleTodo(todo: TodoItem) {
        viewModelScope.launch {
            val today = LocalDate.now().toEpochDay()
            if (!todo.isDone) {
                dao.updateTodo(todo.copy(isDone = true, lastCompletedDate = today))
            } else {
                dao.updateTodo(todo.copy(isDone = false, lastCompletedDate = null))
            }
        }
    }

    fun toggleSelection(id: Int) {
        if (selectedForDeletion.contains(id)) {
            selectedForDeletion.remove(id)
        } else {
            selectedForDeletion.add(id)
        }
    }

    fun deleteSelectedTasks(todos: List<TodoItem>) {
        viewModelScope.launch {
            val toDelete = todos.filter { selectedForDeletion.contains(it.id) }
            toDelete.forEach { dao.deleteTodo(it) }
            selectedForDeletion.clear()
            isDeleteMode = false
        }
    }
}

// ViewModelにDAOを渡すための工場クラス
class TodoViewModelFactory(private val dao: TodoDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TodoViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TodoViewModel(dao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

// 5. Activity
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // データベースの準備
        val db = Room.databaseBuilder(
            applicationContext,
            TodoDatabase::class.java, "todo-database"
        ).build()

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // ViewModelを初期化して画面に渡す
                    val viewModel: TodoViewModel = viewModel(
                        factory = TodoViewModelFactory(db.todoDao())
                    )
                    TodoScreen(viewModel)
                }
            }
        }
    }
}

// 6. UI画面
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoScreen(viewModel: TodoViewModel) {
    // Flowからデータを監視してUIに反映
    val todoList by viewModel.todoList.collectAsState(initial = emptyList())

    // データが更新されたら日付チェックを走らせる
    LaunchedEffect(todoList) {
        if (todoList.isNotEmpty()) {
            viewModel.checkAndResetDailyTasks(todoList)
        }
    }

    val activeTodos = todoList.filter { !it.isDone }
    val completedTodos = todoList.filter { it.isDone }

    var expandedMenu by remember { mutableStateOf(false) }
    var showCompleted by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (viewModel.isDeleteMode) {
                        Text("${viewModel.selectedForDeletion.size}件選択中")
                    } else {
                        Text("ToDoList")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                ),
                actions = {
                    if (viewModel.isDeleteMode) {
                        IconButton(onClick = {
                            viewModel.isDeleteMode = false
                            viewModel.selectedForDeletion.clear()
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "キャンセル")
                        }
                    } else {
                        IconButton(onClick = { expandedMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "メニュー")
                        }
                        DropdownMenu(
                            expanded = expandedMenu,
                            onDismissRequest = { expandedMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("タスクを削除する") },
                                onClick = {
                                    viewModel.isDeleteMode = true
                                    expandedMenu = false
                                }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (viewModel.isDeleteMode) {
                FloatingActionButton(
                    onClick = { viewModel.deleteSelectedTasks(todoList) },
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "選択したタスクを削除")
                }
            } else {
                FloatingActionButton(onClick = { viewModel.showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "タスクを追加")
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(16.dp)) }

            items(activeTodos, key = { it.id }) { todo ->
                TodoRow(
                    todo = todo,
                    isDeleteMode = viewModel.isDeleteMode,
                    isSelectedForDeletion = viewModel.selectedForDeletion.contains(todo.id),
                    onToggleDone = { viewModel.toggleTodo(todo) },
                    onToggleSelection = { viewModel.toggleSelection(todo.id) }
                )
            }

            if (completedTodos.isNotEmpty()) {
                item {
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showCompleted = !showCompleted }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "完了したタスク (${completedTodos.size})",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Icon(
                            imageVector = if (showCompleted) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = "折りたたみ切り替え",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                if (showCompleted) {
                    items(completedTodos, key = { it.id }) { todo ->
                        TodoRow(
                            todo = todo,
                            isDeleteMode = viewModel.isDeleteMode,
                            isSelectedForDeletion = viewModel.selectedForDeletion.contains(todo.id),
                            onToggleDone = { viewModel.toggleTodo(todo) },
                            onToggleSelection = { viewModel.toggleSelection(todo.id) }
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }

        if (viewModel.showAddDialog) {
            AddTodoDialog(
                onDismiss = { viewModel.showAddDialog = false },
                onAdd = { title, isDaily ->
                    viewModel.addTodo(title, isDaily)
                    viewModel.showAddDialog = false
                }
            )
        }
    }
}

@Composable
fun TodoRow(
    todo: TodoItem,
    isDeleteMode: Boolean,
    isSelectedForDeletion: Boolean,
    onToggleDone: () -> Unit,
    onToggleSelection: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (isDeleteMode) onToggleSelection() else onToggleDone()
            }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isDeleteMode) {
            Checkbox(
                checked = isSelectedForDeletion,
                onCheckedChange = { onToggleSelection() },
                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.error)
            )
        } else {
            Checkbox(
                checked = todo.isDone,
                onCheckedChange = { onToggleDone() }
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = todo.title,
                fontSize = 16.sp,
                textDecoration = if (todo.isDone && !isDeleteMode) TextDecoration.LineThrough else TextDecoration.None,
                color = if (todo.isDone && !isDeleteMode) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface
            )

            if (todo.isDaily) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "↻繰り返し",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@Composable
fun AddTodoDialog(onDismiss: () -> Unit, onAdd: (String, Boolean) -> Unit) {
    var inputText by remember { mutableStateOf("") }
    var isDaily by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新しいタスクを追加") },
        text = {
            Column {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    label = { Text("タスク名") },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = isDaily,
                        onCheckedChange = { isDaily = it }
                    )
                    Text("毎日繰り返す")
                }
            }
        },
        confirmButton = {
            Button(onClick = { onAdd(inputText, isDaily) }) {
                Text("追加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}