import { useState } from "react";
import AgeCheck from "./components/AgeCheck";
import DocumentInspector from "./components/DocumentInspector";
import "./App.css";

export default function App() {
  const [view, setView] = useState<"age" | "inspect">("age");

  return (
    <div>
      <div className="tabs">
        <button
          className={view === "age" ? "tab active" : "tab"}
          onClick={() => setView("age")}
        >
          Age check
        </button>
        <button
          className={view === "inspect" ? "tab active" : "tab"}
          onClick={() => setView("inspect")}
        >
          Document inspector
        </button>
      </div>
      {view === "age" ? <AgeCheck /> : <DocumentInspector />}
    </div>
  );
}