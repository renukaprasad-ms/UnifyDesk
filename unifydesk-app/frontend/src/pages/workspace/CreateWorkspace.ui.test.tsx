import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import { mediaApi } from '../../features/media/mediaApi'
import CreateWorkspace from './CreateWorkspace'

vi.mock('../../features/media/mediaApi', () => ({
  mediaApi: {
    initiateCompanyLogoUpload: vi.fn(),
    uploadFileToStorage: vi.fn(),
    completeUpload: vi.fn(),
  },
}))

vi.mock('../../features/workspace/workspaceApi', () => ({
  workspaceApi: {
    create: vi.fn(),
    list: vi.fn(),
  },
}))

vi.mock('../../store/hooks', () => ({
  useAppDispatch: () => vi.fn(),
}))

describe('CreateWorkspace UI hardening', () => {
  it('keeps Create workspace disabled while the logo upload is unresolved', () => {
    vi.mocked(mediaApi.initiateCompanyLogoUpload).mockImplementation(
      () => new Promise(() => undefined),
    )

    const view = render(
      <MemoryRouter>
        <CreateWorkspace />
      </MemoryRouter>,
    )

    const fileInput = view.container.querySelector('input[type="file"]') as HTMLInputElement
    fireEvent.change(fileInput, {
      target: { files: [new File(['logo'], 'logo.png', { type: 'image/png' })] },
    })

    const create = screen.getByRole('button', { name: 'Uploading logo…' })
    expect(create).toBeDisabled()
  })
})
