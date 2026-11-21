import { expect, it } from 'vitest'
import manifest from '../../package.json'

it('uses Ant Design as the frontend component library', () => {
  expect(manifest.dependencies).toHaveProperty('antd')
  expect(Object.keys(manifest.dependencies)).not.toContain('@mui/material')
  expect(Object.keys(manifest.dependencies)).not.toContain('@emotion/react')
  expect(Object.keys(manifest.dependencies)).not.toContain('@emotion/styled')
})
