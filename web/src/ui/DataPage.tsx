import type { ReactNode } from "react";

interface DataPageProps {
  title?: string;
  fit?: boolean;
  description?: ReactNode;
  actions?: ReactNode;
  children: ReactNode;
}

export function DataPage({
  title,
  fit,
  description,
  actions,
  children,
}: DataPageProps) {
  return (
    <section
      className={fit ? "ui-data-page ui-data-page--fit" : "ui-data-page"}
    >
      {(title || description || actions) && (
        <header className="ui-data-page-header">
          <div className="ui-data-page-copy">
            {title && (
              <div className="ui-data-page-heading">
                <h2>{title}</h2>
              </div>
            )}
            {description && <p>{description}</p>}
          </div>
          {actions && <div className="ui-data-actions">{actions}</div>}
        </header>
      )}
      {children}
    </section>
  );
}
