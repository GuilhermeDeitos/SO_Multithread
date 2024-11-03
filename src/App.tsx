import { useState, useEffect } from 'react'
import { Table } from './components/Table'
import { BeforeTable } from './components/BeforeTable'
import { api } from './utils/api'
import './App.css'

export interface Task{
  status: number;
  title: string;
  date: string;
  id: number;

}

function App() {
  const [tasks, setTasks] = useState<Task[]>([])

  useEffect(() => {
    api.get('').then(response => {
      setTasks(response.data)
    })
  }, [])

  

  const [search, setSearch] = useState('')


  
  return (
    <div className="App">
      <header>
        <h1 className="MainText">To-Do List</h1>
      </header>
      <main>
        <BeforeTable search={search} setSearch={setSearch} tasks={tasks} setTasks={setTasks} />

        
        <Table setRows={setTasks} rows={tasks} />
      </main>
    </div>
  )
}

export default App
