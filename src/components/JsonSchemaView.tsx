import React, { useMemo, useState } from 'react';
import { AlertCircle, Check, CheckCircle2, Copy, RotateCcw, Upload } from 'lucide-react';
import { validateProjectInvariants } from '../engine/timelineEngine';
import { TIMELINE_CONSTANTS, TimelineProject } from '../engine/timelineTypes';

interface JsonSchemaViewProps {
  project: TimelineProject;
  onImportProject: (project: TimelineProject) => void;
  onResetSample: () => void;
}

export const JsonSchemaView: React.FC<JsonSchemaViewProps> = ({
  project,
  onImportProject,
  onResetSample,
}) => {
  const [copied, setCopied] = useState(false);
  const [importText, setImportText] = useState('');
  const [importError, setImportError] = useState<string | null>(null);
  const [importSuccess, setImportSuccess] = useState(false);

  const formattedJson = useMemo(() => JSON.stringify(project, null, 2), [project]);
  const audit = useMemo(() => validateProjectInvariants(project), [project]);

  const handleCopy = async () => {
    await navigator.clipboard.writeText(formattedJson);
    setCopied(true);
    setTimeout(() => setCopied(false), 1800);
  };

  const handleImport = () => {
    setImportError(null);
    setImportSuccess(false);
    try {
      const parsed = JSON.parse(importText) as TimelineProject;
      if (
        !parsed ||
        typeof parsed.projectId !== 'string' ||
        !Array.isArray(parsed.tracks) ||
        !Array.isArray(parsed.clips)
      ) {
        setImportError('Invalid .gve.json payload: missing projectId, tracks, or clips.');
        return;
      }
      if (parsed.schemaVersion > TIMELINE_CONSTANTS.SCHEMA_VERSION) {
        setImportError(`Unsupported schemaVersion ${parsed.schemaVersion}.`);
        return;
      }
      const validation = validateProjectInvariants(parsed);
      if (!validation.valid) {
        const failed = validation.checks.filter((c) => !c.passed).map((c) => c.detail);
        setImportError(`Invariant check failed: ${failed.join('; ')}`);
        return;
      }
      onImportProject(parsed);
      setImportSuccess(true);
      setImportText('');
    } catch (err) {
      setImportError(err instanceof Error ? err.message : 'Unable to parse JSON.');
    }
  };

  return (
    <div className="grid grid-cols-1 lg:grid-cols-[1fr_380px] gap-4 items-start">
      <div className="bg-[#13161D] border border-[#242936] rounded-xl overflow-hidden">
        <div className="flex flex-wrap items-center justify-between gap-3 px-4 py-3 border-b border-[#242936] bg-[#101319]">
          <div className="text-xs text-slate-400">
            <span className="font-medium text-slate-200">Autosave Project Snapshot</span>
            <span aria-hidden="true" className="mx-2 text-slate-600">·</span>
            <span className="font-mono">{project.projectId}.gve.json</span>
          </div>
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={onResetSample}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-[#2A3040] bg-[#1A1E28] text-xs font-medium text-slate-300 hover:text-white hover:bg-[#222836] transition-colors cursor-pointer"
            >
              <RotateCcw className="w-3.5 h-3.5" />
              <span>Reset Sample</span>
            </button>
            <button
              type="button"
              onClick={() => {
                const blob = new Blob([formattedJson], { type: 'application/json;charset=utf-8' });
                const url = URL.createObjectURL(blob);
                const a = document.createElement('a');
                a.href = url;
                a.download = `${project.projectId}.gve.json`;
                a.click();
                URL.revokeObjectURL(url);
              }}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-[#2A3040] bg-[#1A1E28] text-xs font-medium text-slate-200 hover:bg-[#222836] transition-colors cursor-pointer"
            >
              <span>Download JSON</span>
            </button>
            <button
              type="button"
              onClick={handleCopy}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-[#2A3040] bg-[#1A1E28] text-xs font-medium text-slate-200 hover:bg-[#222836] transition-colors cursor-pointer"
            >
              {copied ? <Check className="w-3.5 h-3.5 text-teal-400" /> : <Copy className="w-3.5 h-3.5" />}
              <span>{copied ? 'Copied' : 'Copy JSON'}</span>
            </button>
          </div>
        </div>
        <pre className="p-4 max-h-[600px] overflow-auto bg-[#0B0D11] font-mono text-xs leading-5 text-slate-200">
          {formattedJson}
        </pre>
      </div>

      <div className="space-y-4">
        <div className="bg-[#13161D] border border-[#242936] rounded-xl p-4 space-y-3">
          <div className="flex items-center justify-between">
            <h3 className="text-sm font-semibold text-white">Timeline Invariant Audit</h3>
            <span className={`text-xs font-medium ${audit.valid ? 'text-teal-400' : 'text-red-400'}`}>
              {audit.valid ? 'All checks passed' : 'Violations detected'}
            </span>
          </div>
          <ul className="divide-y divide-[#1E2330]">
            {audit.checks.map((check) => (
              <li key={check.name} className="py-2.5 flex items-start gap-2.5">
                {check.passed ? (
                  <CheckCircle2 className="w-4 h-4 text-teal-400 shrink-0 mt-0.5" />
                ) : (
                  <AlertCircle className="w-4 h-4 text-red-400 shrink-0 mt-0.5" />
                )}
                <div className="min-w-0">
                  <div className="text-xs font-medium text-slate-200">{check.name}</div>
                  <div className="text-xs text-slate-400 mt-0.5 font-mono">{check.detail}</div>
                </div>
              </li>
            ))}
          </ul>
        </div>

        <div className="bg-[#13161D] border border-[#242936] rounded-xl p-4 space-y-3">
          <div>
            <h3 className="text-sm font-semibold text-white">Validate & Load `.gve.json`</h3>
            <p className="text-xs text-slate-400 mt-1">
              Paste serialized project JSON matching <span className="font-mono text-slate-300">ProjectJsonSerializer</span> to validate and load it into the timeline engine.
            </p>
          </div>
          <textarea
            value={importText}
            onChange={(e) => setImportText(e.target.value)}
            rows={7}
            placeholder='{"schemaVersion": 1, "projectId": "...", ...}'
            className="w-full rounded-lg bg-[#0B0D11] border border-[#242936] p-3 text-xs font-mono text-slate-200 placeholder:text-slate-600 focus:outline-none focus:border-teal-500"
          />
          {importError && <p className="text-xs text-red-400">{importError}</p>}
          {importSuccess && (
            <p className="text-xs text-teal-400">Project validated and loaded into workspace.</p>
          )}
          <button
            type="button"
            onClick={handleImport}
            disabled={!importText.trim()}
            className="w-full flex items-center justify-center gap-2 px-4 py-2 rounded-lg bg-teal-500 hover:bg-teal-400 text-slate-950 font-semibold text-xs disabled:opacity-40 disabled:cursor-not-allowed transition-colors cursor-pointer"
          >
            <Upload className="w-3.5 h-3.5" />
            <span>Validate & Load Project</span>
          </button>
        </div>
      </div>
    </div>
  );
};
