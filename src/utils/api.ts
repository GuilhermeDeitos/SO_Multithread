import axios from "axios";

export const api = axios.create({
  baseURL: "http://26.126.214.168:3000/api/tasks",
});

