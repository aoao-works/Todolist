package com.example.todolist // ※ご自身のプロジェクトのパッケージ名に書き換えてください

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
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate

// 1. データモデル
data class TodoItem(
    val id: Int,
    val title: String,
    var isDone: Boolean = false,
    val isDaily: Boolean = false,
    var lastCompletedDate: Long? = null
)

// 2. ViewModel
class TodoViewModel : ViewModel() {
    private var _todoList = mutableStateListOf<TodoItem>()
    val todoList: List<TodoItem> get() = _todoList
    private var nextId = 1

    var isDeleteMode by mutableStateOf(false)
    var selectedForDeletion = mutableStateListOf<Int>()
    var showAddDialog by mutableStateOf(false)

    val activeTodos: List<TodoItem> get() = _todoList.filter { !it.isDone }
    val completedTodos: List<TodoItem> get() = _todoList.filter { it.isDone }

    init {
        checkAndResetDailyTasks()
    }

    fun checkAndResetDailyTasks() {
        val today = LocalDate.now().toEpochDay()
        for (i in _todoList.indices) {
            val item = _todoList[i]
            if (item.isDaily && item.isDone && item.lastCompletedDate != today) {
                _todoList[i] = item.copy(isDone = false, lastCompletedDate = null)
            }
        }
    }

    fun addTodo(title: String, isDaily: Boolean) {
        if (title.isNotBlank()) {
            _todoList.add(TodoItem(id = nextId++, title = title, isDaily = isDaily))
        }
    }

    fun toggleTodo(id: Int) {
        val index = _todoList.indexOfFirst { it.id == id }
        if (index != -1) {
            val item = _todoList[index]
            val today = LocalDate.now().toEpochDay()
            if (!item.isDone) {
                _todoList[index] = item.copy(isDone = true, lastCompletedDate = today)
            } else {
                _todoList[index] = item.copy(isDone = false, lastCompletedDate = null)
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

    fun deleteSelectedTasks() {
        _todoList.removeAll { selectedForDeletion.contains(it.id) }
        selectedForDeletion.clear()
        isDeleteMode = false
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TodoScreen()
                }
            }
        }
    }
}

// 3. UI画面
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoScreen(viewModel: TodoViewModel = viewModel()) {
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
                    onClick = { viewModel.deleteSelectedTasks() },
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

            // ① 未完了のタスクリスト
            items(viewModel.activeTodos, key = { it.id }) { todo ->
                TodoRow(
                    todo = todo,
                    isDeleteMode = viewModel.isDeleteMode,
                    isSelectedForDeletion = viewModel.selectedForDeletion.contains(todo.id),
                    onToggleDone = { viewModel.toggleTodo(todo.id) },
                    onToggleSelection = { viewModel.toggleSelection(todo.id) }
                )
            }

            // ② 完了済みタスクの折りたたみタブ
            if (viewModel.completedTodos.isNotEmpty()) {
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
                            text = "完了したタスク (${viewModel.completedTodos.size})",
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
                    items(viewModel.completedTodos, key = { it.id }) { todo ->
                        TodoRow(
                            todo = todo,
                            isDeleteMode = viewModel.isDeleteMode,
                            isSelectedForDeletion = viewModel.selectedForDeletion.contains(todo.id),
                            onToggleDone = { viewModel.toggleTodo(todo.id) },
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

// 4. 各タスクの行
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

            // ▼ ここを変更しました：1回限りの分岐を消し、繰り返しのテキストを変更
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

// 5. タスク追加ダイアログ
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