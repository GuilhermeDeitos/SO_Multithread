import {
  BsFillTrash3Fill,
  BsFillPencilFill,
  BsCalendar3,
} from "react-icons/bs";
import {
  TbAbc,
  TbAlertCircle,
  TbLoader,
  TbAlertSquare,
  TbCheckbox,
  TbClock,
  TbCirclePlus,
  TbArrowAutofitDown,
} from "react-icons/tb";

import Swal from "sweetalert2";
import { Task } from "../../App";
import "./style.css";
import { useState, useEffect } from "react";
import { api } from "../../utils/api";

interface TableProps {
  rows: Task[];
  setRows: (rows: Task[]) => void;
}

const setRowStatus = (status: number): React.ReactNode => {
  const dictStatus: { [key: number]: React.ReactNode } = {
    0: (
      <span className="status status-pendente">
        <TbAlertCircle />
        <span>Pendente</span>
      </span>
    ),
    1: (
      <span className="status status-andamento">
        <TbClock />
        <span>Andamento</span>
      </span>
    ),
    2: (
      <span className="status status-concluido">
        <TbCheckbox />
        <span>Concluído</span>
      </span>
    ),
  };
  return dictStatus[status];
};

const transformDate = (date: string): string => {
  const [year, month, day] = date.split("-");
  return `${day}/${month}/${year}`;
};

export function Table({ rows, setRows }: TableProps): React.ReactElement {
  const [editTaskId, setEditTaskId] = useState<number | null>(null); // Controla qual tarefa está sendo editada
  const [loading, setLoading] = useState(false); // Controla o estado de carregamento
  const [order, setOrder] = useState<boolean>(false); // Controla a ordenação
  const [taskAquired, setTaskAquired] = useState<bool>(false); // Controla a tarefa adquirida

  // Função para impedir exclusão se a tarefa estiver sendo editada
  function handleDeleteTask(e: React.MouseEvent, task: Task) {
    console.log(task)
    api.post('/acquire',task).then(result => {
      setTaskAquired(true);
      console.log('Tarefa adquirida com sucesso!')
    }).catch((error) => {
      console.log(error.response);
      Swal.fire({
        icon: "info",
        title: error.response.data,
      });
    })
    Swal.fire({
      icon: "warning",
      title: "Deseja excluir a tarefa?",
      showDenyButton: true,
      confirmButtonText: `Sim`,
      denyButtonText: `Não`,
    }).then((result) => {
      if (result.isConfirmed) {
        api.post(`/release`).then((result) => {
          console.log(result);
          setTaskAquired(false);

        })
        api
          .delete(`/${task.id}`)
          .then((result) => {
            const updatedRows = rows.filter((row) => row.id !== task.id);
            Swal.fire({
              icon: "success",
              title: "Tarefa excluída com sucesso!",
            });
            setRows(updatedRows);
            
          })
          .catch((error) => {
            console.log(error);
            if(error.response.status === 429){
              Swal.fire({
                icon: "error",
                title: error.response.data
              });
            }
            Swal.fire({
              icon: "error",
              title: "Erro ao excluir tarefa!",
            });
          });
      } else {
        api.post('/release').then(result => {
          console.log('Tarefa liberada com sucesso!')
      setTaskAquired(false);
    
        }).catch((error) => {
          console.log(error);
          Swal.fire({
            icon: "info",
            title: error.response.data,
          });
        })
      }
    });
  }

  function handleEditTask(task: Task) {
    api.post(`/acquire`,task).then((result) => {
      console.log(result);
      setEditTaskId(task.id); // Ativa a edição da tarefa
      setTaskAquired(true);

    }).catch((error) => {
      console.log(error.response);
      Swal.fire({
        icon: "info",
        title: error.response.data,
      });
    })
  }

  function handleSaveTask(e: React.MouseEvent, task: Task) {
    api.post(`/release`).then((result) => {
      console.log(result);
      setEditTaskId(task.id); // Ativa a edição da tarefa
      setTaskAquired(false);

    })

    api
      .put(`/${task.id}`, task)
      .then((response) => {
        Swal.fire({
          icon: "success",
          title: "Tarefa salva com sucesso!",
        });
        setEditTaskId(null); // Limpa o estado de edição
      })
      .catch((error) => {
        console.log(error);
        Swal.fire({
          icon: "error",
          title: "Erro ao salvar tarefa!",
        });
      });
  }

  function handleCancelTask() {
    api.post(`/release`).then((result) => {
      setTaskAquired(false);

      console.log(result);
    }).catch((error) => {
      console.log(error.response);
      
    })
    setEditTaskId(null); // Cancela a edição e libera a tarefa
  }

  function handleOrderTask(){
    setOrder(!order);
    api.get(order ? "/order" : "").then((response) => {
      setRows(response.data);
    }).catch((error) => {
      console.log(error);
      Swal.fire({
      icon: "error",
      title: "Erro ao ordenar tarefas!",
      });
    });
  }

  // Função para atualizar automaticamente quando novas tarefas forem adicionadas
  useEffect(() => {
    console.log(order)
    if(taskAquired) {
      return;
    }
    const interval = setInterval(() => {
      api.get(order ?  "/order" :"").then((response) => {
        setRows(response.data);
      });
    }, 3000); // Atualiza a cada 5 segundos

    return () => clearInterval(interval); // Limpa o intervalo ao desmontar o componente
  }, [order, setRows, taskAquired]);

  return (
    <table>
      <thead>
        <tr>
          <th style={{ width: "10%" }}>
            <span>
              <TbLoader />
              Status
            </span>
          </th>
          <th style={{ width: "30%" }} onClick={handleOrderTask}>
            <span>
              <TbAbc />
              Nome da Tarefa
              <TbArrowAutofitDown />
            </span>
          </th>
          <th style={{ width: "10%" }}>
            <span>
              <BsCalendar3 />
              Data inclusão
            </span>
          </th>
          <th style={{ width: "10%" }}>
            <span>
              <TbAlertSquare />
              Ações
            </span>
          </th>
        </tr>
      </thead>
      <tbody>
        {rows.map((row, index) => (
          <tr key={index}>
            {editTaskId === row.id ? (
              <>
                <td>
                  <select
                    name="status"
                    id="status"
                    className="input-edit"
                    value={row.status}
                    onChange={(e) =>
                      setRows(
                        rows.map((r) =>
                          r.id === row.id
                            ? { ...r, status: parseInt(e.target.value) }
                            : r
                        )
                      )
                    }
                  >
                    <option value="0">Pendente</option>
                    <option value="1">Andamento</option>
                    <option value="2">Concluído</option>
                  </select>
                </td>
                <td>
                  <input
                    type="text"
                    name="title"
                    className="input-edit"
                    value={row.title}
                    onChange={(e) =>
                      setRows(
                        rows.map((r) =>
                          r.id === row.id
                            ? { ...r, title: e.target.value }
                            : r
                        )
                      )
                    }
                  />
                </td>
                <td>
                  <input
                    type="date"
                    name="date"
                    className="input-edit"
                    value={row.date}
                    onChange={(e) =>
                      setRows(
                        rows.map((r) =>
                          r.id === row.id
                            ? { ...r, date: e.target.value }
                            : r
                        )
                      )
                    }
                  />
                </td>
                <td className="actions">
                  <button className="btn btn-expandir" onClick={(e) => handleSaveTask(e, row)}>
                    <TbCirclePlus /> Salvar
                  </button>
                  <button className="btn btn-excluir" onClick={handleCancelTask}>
                    <TbAlertSquare /> Cancelar
                  </button>
                </td>
              </>
            ) : (
              <>
                <td>{setRowStatus(row.status)}</td>
                <td>{row.title}</td>
                <td>{transformDate(row.date)}</td>
                <td className="actions">
                  <button className="btn btn-editar" onClick={() => handleEditTask(row)}>
                    <BsFillPencilFill /> Editar
                  </button>
                  <button className="btn btn-excluir" onClick={(e) => handleDeleteTask(e, row)}>
                    <BsFillTrash3Fill /> Excluir
                  </button>
                </td>
              </>
            )}
          </tr>
        ))}
      </tbody>
    </table>
  );
}
